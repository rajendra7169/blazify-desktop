package com.blazify.desktop.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Net
import com.blazify.desktop.data.Streams
import com.blazify.desktop.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The song's music video, playing silently behind the player.
 *
 * A second player of its own, entirely apart from [AudioEngine]: it is told to
 * make no sound at all, it is handed a stream with no sound in it, and nothing
 * it does — failing, stalling, being thrown away mid-song — can reach what you
 * hear. The audio is the song; this is a picture of it, and the worst it is
 * allowed to do is not appear.
 *
 * Frames are copied out of the player into memory of our own and drawn by the
 * window like any other picture, rather than by a native panel laid over it. A
 * native panel always sits on top of everything, so the fade into the page and
 * the controls drawn over the picture would both be impossible.
 */
object VideoEngine {

    private const val NO_PICTURE_NS = 15_000_000_000L
    private const val RETRY_AFTER_MS = 30_000L
    private const val MOST_REFUSED = 4

    /** Goes up by one for every frame, so whatever draws the picture redraws. */
    var frames by mutableStateOf(0L)
        private set

    /**
     * Whether the picture is ready to be seen.
     *
     * Not the first frame: the first few are looked at for black bands before
     * anything is shown, so the picture appears at the size it will keep.
     */
    var revealed by mutableStateOf(false)
        private set

    /** Rows trimmed off the top and the bottom of each frame as black band. */
    var trimmed by mutableStateOf(0)
        private set

    /** The song whose video this is, so a stale picture is never drawn under the next one. */
    var showingFor by mutableStateOf<String?>(null)
        private set

    /** Which video is up, for the tools that report on this. */
    @Volatile var playingVideo: String? = null
        private set

    /**
     * One thread of its own for every instruction to the player.
     *
     * Stopping a player that is fetching over the network can take a moment,
     * and the window should not wait on it. Putting it on a thread of its own
     * keeps the window free, and putting everything on the same one keeps the
     * order: a song change can never start the next video before the last one
     * has finished stopping.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor { work ->
            Thread(work, "video").apply { isDaemon = true }
        }.asCoroutineDispatcher(),
    )
    private var job: Job? = null
    private var wantedFor: String? = null

    /** Paused because nobody can see it — the window is down in the taskbar. */
    @Volatile private var held = false

    // ── the frames ──

    private val lock = Any()
    private var pixels = ByteArray(0)
    private var width = 0
    private var height = 0

    /**
     * The part of each frame that is picture.
     *
     * The player hands frames over in a buffer it may round up to a size its
     * decoder likes, and anything past the picture is whatever happened to be
     * in memory. Drawing only this much keeps a stripe of noise off the edge.
     */
    @Volatile var pictureWidth = 0
        private set
    @Volatile var pictureHeight = 0
        private set

    /** Whether frames are welcome. Cleared before the player stops, so a late one is dropped. */
    @Volatile private var taking = false

    /** Frames seen since this video began, for deciding when to look for bands. */
    private var seen = 0
    private var thinnest: Double? = null
    private var looked = 0
    private var firstFrameAt = 0L

    /** When a frame last arrived, for noticing a stream that has given nothing or stopped. */
    @Volatile private var lastFrameAt = 0L

    /**
     * The newest frame as a picture, or nothing yet.
     *
     * Copied under the lock, so the player can carry on writing the next one
     * while this one is drawn. The caller closes it once drawn.
     */
    fun snapshot(): Image? = synchronized(lock) {
        if (width == 0 || height == 0 || pixels.isEmpty()) return null
        Image.makeRaster(
            ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
            pixels,
            width * 4,
        )
    }

    private object Format : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            synchronized(lock) {
                width = sourceWidth
                height = sourceHeight
                pixels = ByteArray(sourceWidth * sourceHeight * 4)
            }
            pictureWidth = sourceWidth
            pictureHeight = sourceHeight
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {
            if (displayWidth > 0) pictureWidth = displayWidth.coerceAtMost(bufferWidth)
            if (displayHeight > 0) pictureHeight = displayHeight.coerceAtMost(bufferHeight)
        }
    }

    private object Frames : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) {}
        override fun unlock(mediaPlayer: MediaPlayer) {}

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int,
        ) {
            if (!taking) return
            lastFrameAt = System.nanoTime()
            val source = nativeBuffers.firstOrNull() ?: return
            synchronized(lock) {
                if (pixels.isEmpty()) return
                source.rewind()
                source.get(pixels, 0, minOf(pixels.size, source.remaining()))
                source.rewind()
                if (!revealed) look()
            }
            frames++
        }
    }

    /**
     * Decide the bands before showing anything.
     *
     * A handful of frames spread over the first second or so, keeping the
     * thinnest band any of them shows: a frame with a dark top to its picture
     * would otherwise read as a band and crop away something that belongs.
     * Frames too dark to judge are left out. Shown after the last look, or
     * after two seconds whatever has been seen, so a video that is dark from
     * the start still appears.
     */
    private fun look() {
        seen++
        if (seen == 1) firstFrameAt = System.nanoTime()
        val tall = pictureHeight.takeIf { it in 1..height } ?: height
        if (seen == 2 || seen == 10 || seen == 20 || seen == 32) {
            looked++
            Letterbox.share(pixels, width, tall)?.let { found ->
                thinnest = thinnest?.let { minOf(it, found) } ?: found
            }
        }
        val waited = (System.nanoTime() - firstFrameAt) / 1_000_000_000.0
        if (looked >= 4 || waited > 2.0) {
            trimmed = Letterbox.trim(thinnest ?: 0.0, tall)
            revealed = true
        }
    }

    // ── the player ──

    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null

    /**
     * Built on first use and thrown away when the look is put away.
     *
     * Asks the audio side first whether the library is there at all: that is
     * also what points a Windows install at the copy it carries, and doing it
     * twice in two places is how the two would end up disagreeing.
     */
    private fun player(): EmbeddedMediaPlayer? {
        player?.let { return it }
        if (!AudioEngine.available()) return null
        return runCatching {
            val made = MediaPlayerFactory(
                // No sound at all, from the machinery up. This is the only
                // silencing it gets: the player's own mute is not a private
                // switch. Windows keeps volume per program rather than per
                // player, and Linux keeps it per kind of stream, so muting this
                // one can silence the song as well.
                "--no-audio",
                "--quiet",
                "--no-video-title-show",
                // Decoded in software. A graphics card hands its frames back
                // in a form of its own that cannot be turned into plain
                // pixels for the window, and the player then shows nothing at
                // all. At 480 lines the processor does not notice the work.
                "--avcodec-hw=none",
                // Decoded into memory of its own. Straight into the pictures
                // waiting to be shown, the decoder runs out of them while the
                // clock is still at the start or after a jump, loses a frame
                // others are built on, and shows grey blocks until the next
                // whole picture comes along.
                "--no-avcodec-dr",
                "--no-avcodec-corrupted",
            )
            val p = made.mediaPlayers().newEmbeddedMediaPlayer()
            p.videoSurface().set(made.videoSurfaces().newVideoSurface(Format, Frames, true))
            factory = made
            player = p
            p
        }.getOrNull()
    }

    /**
     * Show this song's video, finding it if it isn't already up.
     *
     * Nothing happens on a metered connection — the data saver is the setting
     * that says so — or with no connection at all: the cover stays, and a
     * picture is not worth a byte somebody is counting. Every other song gets
     * as long a search as it takes; see [run].
     */
    fun show(track: Track) {
        scope.launch { showNow(track) }
    }

    private fun showNow(track: Track) {
        if (track.id == wantedFor) return
        stopVideo()
        wantedFor = track.id
        if (!Net.online || Streams.quality == Streams.Quality.Saver) return
        job = scope.launch { run(track) }
    }

    /** Pause it while the window is out of sight; it carries on when the window is back. */
    fun hold(value: Boolean) {
        held = value
        if (value) scope.launch { runCatching { player?.controls()?.setPause(true) } }
    }

    /** The look is gone: stop, and give back everything the player holds. */
    fun hide() {
        scope.launch { hideNow() }
    }

    private fun hideNow() {
        stopVideo()
        wantedFor = null
        val p = player
        val f = factory
        player = null
        factory = null
        runCatching { p?.release() }
        runCatching { f?.release() }
    }

    private fun stopVideo() {
        job?.cancel()
        job = null
        clearPicture()
    }

    /** Stop the stream and forget its pictures, leaving the search for another one running. */
    private fun clearPicture() {
        taking = false
        runCatching { player?.controls()?.stop() }
        synchronized(lock) {
            pixels = ByteArray(0)
            width = 0
            height = 0
            pictureWidth = 0
            pictureHeight = 0
            seen = 0
            looked = 0
            thinnest = null
        }
        revealed = false
        trimmed = 0
        showingFor = null
    }

    /**
     * Keep going until a picture is on screen.
     *
     * Each video is tried from two sources, and each video on the list after
     * that, because a stream can be handed over and still be refused when it is
     * fetched. One look round again after a wait covers a connection that came
     * back. The limit is on streams that gave no picture: past a few of those
     * the trouble is here rather than with the video, and asking on for every
     * song only gets this address noticed by YouTube.
     */
    private suspend fun run(track: Track) {
        var refused = 0
        repeat(2) { round ->
            if (round > 0) delay(RETRY_AFTER_MS)
            for (chosen in Catalogue.musicVideos(track)) {
                var from = 0
                for (attempt in 1..2) {
                    if (!currentCoroutineContext().isActive || wantedFor != track.id) return
                    val picture = Catalogue.picture(chosen.videoId, from).getOrNull() ?: break
                    from = picture.next
                    if (play(track, chosen, picture.stream)) return
                    if (++refused >= MOST_REFUSED) {
                        clearPicture()
                        return
                    }
                }
            }
        }
        clearPicture()
    }

    /** Play one stream for as long as it is wanted. False if it gave no picture, or stopped giving one. */
    private suspend fun play(track: Track, chosen: Catalogue.MusicVideo, stream: Catalogue.Stream): Boolean {
        clearPicture()
        val p = player() ?: return true

        val songLength = AudioEngine.duration.takeIf { it > 0 } ?: track.durationSeconds?.toDouble()
        val options = buildList {
            add(":http-user-agent=${stream.userAgent}")
            add(":network-caching=3000")
            add(":no-audio")
            if (chosen.inStep) {
                add(":start-time=${"%.2f".format(Locale.ROOT, AudioEngine.position)}")
            } else {
                // Its own cut, so it runs on its own: started about as far
                // through itself as the song is through the song, and looped
                // so a short video does not leave the box empty before the
                // song is over.
                add(":start-time=${startOf(chosen.lengthSeconds, songLength)}")
                add(":input-repeat=65535")
            }
        }.toTypedArray()

        playingVideo = chosen.videoId
        showingFor = track.id
        taking = true
        lastFrameAt = System.nanoTime()
        if (!runCatching { p.media().play(stream.url, *options) }.getOrDefault(false)) return false
        if (held || !AudioEngine.playing) runCatching { p.controls().setPause(true) }

        var rate = 1f
        while (currentCoroutineContext().isActive && wantedFor == track.id) {
            delay(250)
            val shouldPlay = AudioEngine.playing && !held
            when (runCatching { p.status().state() }.getOrNull()) {
                State.ERROR -> return false
                State.ENDED -> return true
                else -> {}
            }
            val running = runCatching { p.status().isPlaying }.getOrDefault(false)
            if (shouldPlay != running) runCatching { p.controls().setPause(!shouldPlay) }

            // No frames are owed while it is told to wait, or while the song
            // itself is waiting on the network.
            if (!shouldPlay || AudioEngine.buffering || AudioEngine.loading) {
                lastFrameAt = System.nanoTime()
                continue
            }
            if (System.nanoTime() - lastFrameAt > NO_PICTURE_NS) return false

            if (chosen.inStep) rate = steer(p, rate)
        }
        return true
    }

    /**
     * Keep a same-cut video on the song's beat.
     *
     * Small differences are closed by running the picture a touch fast or a
     * touch slow until it catches up — invisible, where a jump is a cut nobody
     * edited. Only a difference too big to catch up on quietly is jumped:
     * after a seek, or a stall on one side.
     */
    private fun steer(p: EmbeddedMediaPlayer, rate: Float): Float {
        if (AudioEngine.buffering || AudioEngine.loading) return rate
        val video = runCatching { p.status().time() }.getOrDefault(-1L)
        if (video < 0) return rate
        val drift = video / 1000.0 - AudioEngine.position

        if (abs(drift) > 1.5) {
            runCatching { p.controls().setTime((AudioEngine.position * 1000).toLong()) }
            if (rate != 1f) runCatching { p.controls().setRate(1f) }
            return 1f
        }

        // Ahead slows it, behind speeds it, in proportion — and never by more
        // than a few per cent, where a change of pace would start to show.
        val wanted = if (abs(drift) < 0.06) 1f else (1.0 - drift * 0.5).coerceIn(0.94, 1.06).toFloat()
        if (abs(wanted - rate) > 0.005f) runCatching { p.controls().setRate(wanted) }
        return wanted
    }

    /**
     * Where an independent video starts.
     *
     * As far through itself as the song is through the song, so changing to
     * this look half way through a song does not begin on the opening titles.
     * Never earlier than a little way in for the same reason, and never so
     * late that it is about to end.
     */
    private fun startOf(videoLength: Int?, songLength: Double?): String {
        val length = videoLength?.toDouble() ?: return "15"
        val through = if (songLength != null && songLength > 0) AudioEngine.position / songLength else 0.0
        val earliest = maxOf(10.0, length * 0.08).coerceAtMost(length / 2)
        val latest = (length - 10.0).coerceAtLeast(earliest)
        val at = (through * length).coerceIn(earliest, latest)
        return "%.2f".format(Locale.ROOT, at)
    }
}
