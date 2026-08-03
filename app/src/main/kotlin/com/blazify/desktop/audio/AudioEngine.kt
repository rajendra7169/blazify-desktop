package com.blazify.desktop.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Plays one track at a time.
 *
 * Built on a native audio component rather than a JVM media library, for one
 * concrete reason: the catalogue serves fragmented MP4, and the lighter options
 * refuse to open it at all. This one takes it as it comes.
 *
 * Every callback arrives on a native thread, so nothing here touches anything
 * beyond the state fields — those are observable, and the UI reads them.
 */
object AudioEngine {

    var position by mutableStateOf(0.0)      // seconds
        private set
    var duration by mutableStateOf(0.0)      // seconds
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Called when a track reaches its end, so the queue can move on. */
    var onFinished: (() -> Unit)? = null

    /**
     * The player reports the time it has reached, but only every few hundred
     * milliseconds — enough to move a progress bar, nowhere near enough to
     * follow a line of a song. So the last report is kept with the moment it
     * arrived, and the time between reports is carried by the wall clock.
     *
     * The result is a position that moves continuously and is corrected by the
     * truth several times a second, rather than one that jumps.
     */
    private var anchor = 0.0
    private var anchoredAt = System.nanoTime()

    /**
     * When the player last said anything about where it was.
     *
     * The clock is interpolated between reports, which is what makes the bar
     * move smoothly — but it means that if the audio stops arriving and the
     * player goes quiet, the interpolation carries on inventing a position for
     * a song that is no longer playing. A counter that keeps counting through
     * silence is worse than one that stops: it says everything is fine.
     */
    private var lastHeardFrom = System.nanoTime()

    /** Whether this media has reported a position yet. */
    private var heardOnce = false

    /** True when the player has gone quiet while it was supposed to be playing. */
    var stalled by mutableStateOf(false)
        private set

    private fun anchorAt(seconds: Double) {
        heardOnce = true
        anchor = seconds
        anchoredAt = System.nanoTime()
        lastHeardFrom = System.nanoTime()
        stalled = false
        position = seconds
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (true) {
                delay(40)
                if (!playing) continue

                // Nothing is judged until the player has reported a position at
                // least once for this media. Before that there is no silence to
                // measure — only a stream that has not started yet.
                if (!heardOnce) continue

                val quietFor = (System.nanoTime() - lastHeardFrom) / 1_000_000_000.0
                // Five seconds without a word. Reports arrive several times a
                // second when all is well, so this is not a slow machine — it
                // is a stream that has stopped arriving. Generous, because the
                // cost of being wrong is restarting a song that was fine.
                if (quietFor > 5.0) {
                    stalled = true
                    continue
                }

                val since = (System.nanoTime() - anchoredAt) / 1_000_000_000.0
                val guess = anchor + since
                position = if (duration > 0) guess.coerceAtMost(duration) else guess
            }
        }
    }

    /**
     * Created lazily. Building it loads the native library, and doing that at
     * startup would slow the window down for someone who never presses play.
     */
    private val component: AudioPlayerComponent by lazy {
        useBundledLibrary()
        AudioPlayerComponent().also { it.mediaPlayer().events().addMediaPlayerEventListener(Listener) }
    }

    /**
     * Point the player at the copy that travels with the application.
     *
     * Only Windows carries one — a Linux package declares a dependency and the
     * package manager puts a shared copy where it belongs, which is both
     * smaller and more correct. Windows has no such mechanism, so the library
     * ships inside the application and is found here.
     *
     * Silent when there's nothing bundled: running from source, or on a system
     * that has its own, the ordinary search finds it.
     */
    private fun useBundledLibrary() {
        runCatching {
            val root = System.getProperty("compose.application.resources.dir") ?: return
            val folder = java.io.File(root, "vlc")
            if (!java.io.File(folder, "libvlc.dll").exists()) return

            com.sun.jna.NativeLibrary.addSearchPath("libvlc", folder.absolutePath)
            System.setProperty("jna.library.path", folder.absolutePath)

            // The decoders sit in a folder beside the library, and the library
            // is told where by the environment rather than by an argument.
            com.sun.jna.platform.win32.Kernel32.INSTANCE.SetEnvironmentVariable(
                "VLC_PLUGIN_PATH",
                java.io.File(folder, "plugins").absolutePath,
            )
        }
    }

    private val player: MediaPlayer get() = component.mediaPlayer()

    private object Listener : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) {
            playing = true
            loading = false
            // The clock starts being watched from here, not from when the media
            // was handed over. Fetching and opening a stream can take several
            // seconds, and counting that silence as a stall meant every song
            // was declared broken the instant it began.
            lastHeardFrom = System.nanoTime()
            stalled = false
        }

        override fun paused(mediaPlayer: MediaPlayer) { playing = false }

        override fun stopped(mediaPlayer: MediaPlayer) {
            playing = false
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            playing = false
            anchorAt(duration)
            // The callback arrives on a native thread and starting the next
            // track from inside it deadlocks the player, so hand it off.
            Thread { onFinished?.invoke() }.start()
        }

        override fun error(mediaPlayer: MediaPlayer) {
            loading = false
            playing = false
            error = "This track wouldn't play"
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            if (newLength > 0) duration = newLength / 1000.0
            // A reopened stream knows its length before it can be seeked; this
            // is the first moment the jump back will actually take.
            resumeAt?.let { target ->
                if (duration > 0) {
                    resumeAt = null
                    runCatching { player.controls().setPosition((target / duration).toFloat()) }
                    anchorAt(target)
                }
            }
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            anchorAt(newTime / 1000.0)
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            // Buffering is the player still talking, so the clock is not lying
            // — but it is also not moving, so the anchor is refreshed to stop
            // the interpolation running ahead of the audio.
            lastHeardFrom = System.nanoTime()
            if (newCache >= 100f) stalled = false
        }
    }

    /** Whether the native library could be found. False means VLC isn't installed. */
    fun available(): Boolean = runCatching { component; true }.getOrDefault(false)

    /**
     * The library's own equaliser, if it's there.
     *
     * Handed out rather than wrapped: the bands and presets belong to whatever
     * version of the library is installed, and inventing our own list would
     * only be right until it changed.
     */
    fun equalizerApi(): uk.co.caprica.vlcj.factory.EqualizerApi? =
        runCatching { component.mediaPlayerFactory().equalizer() }.getOrNull()

    /** Attach a curve, or detach whatever is on. */
    fun applyEqualizer(curve: uk.co.caprica.vlcj.player.base.Equalizer?) {
        runCatching { player.audio().setEqualizer(curve) }
    }

    /**
     * Play from a point rather than the start.
     *
     * Used when a stream has to be opened again: the listener was three minutes
     * into something, and starting them at the beginning would be a worse
     * failure than the one being recovered from.
     */
    fun play(mrl: String, startAt: Double, userAgent: String? = null) {
        play(mrl, userAgent)
        if (startAt > 1.0) {
            resumeAt = startAt
        }
    }

    /** Where to jump to once the new stream reports a length. */
    private var resumeAt: Double? = null

    fun play(mrl: String, userAgent: String? = null) {
        error = null
        loading = true
        resumeAt = null
        heardOnce = false
        stalled = false
        anchorAt(0.0)
        duration = 0.0
        // Told who to claim to be. The catalogue ties a stream link to the kind
        // of device that asked for it and turns away anyone else — so fetching
        // it as the library's own default is a refusal, and a refusal here
        // looks exactly like a track that will not play.
        val options = buildList {
            userAgent?.let { add(":http-user-agent=$it") }
            // Enough held ahead that a hiccup on the line is not a silence.
            add(":network-caching=3000")
        }.toTypedArray()
        runCatching { player.media().play(mrl, *options) }
            .onFailure {
                loading = false
                error = it.message ?: "This track wouldn't play"
            }
    }

    fun toggle() {
        runCatching {
            if (player.status().isPlaying) player.controls().pause() else player.controls().play()
        }
        // Resuming counts on from here, not from whenever the last report was.
        anchorAt(position)
    }

    fun seek(fraction: Double) {
        val target = fraction.coerceIn(0.0, 1.0)
        runCatching { player.controls().setPosition(target.toFloat()) }
        // Moved now rather than waiting for the player to say so, or the
        // in-between would be spent counting up from where you just left.
        if (duration > 0) anchorAt(target * duration)
    }

    fun setVolume(value: Double) {
        runCatching { player.audio().setVolume((value.coerceIn(0.0, 1.0) * 100).toInt()) }
    }

    fun stop() {
        runCatching { player.controls().stop() }
        playing = false
        anchorAt(0.0)
        duration = 0.0
    }
}
