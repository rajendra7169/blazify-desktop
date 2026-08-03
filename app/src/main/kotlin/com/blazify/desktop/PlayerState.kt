package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.LyricsSource
import com.blazify.desktop.data.Playback
import com.blazify.desktop.data.Scrobbler
import com.blazify.desktop.together.Did
import com.blazify.desktop.together.Together
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Track
import com.blazify.desktop.data.asTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What's playing, and the queue it came from.
 *
 * Sits between the screens and the engine: screens say "play this list from
 * here", and everything about resolving a URL and advancing at the end of a
 * track happens on this side.
 */
object PlayerState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    var failure by mutableStateOf<String?>(null)
        private set

    val current: Track? get() = queue.getOrNull(index)
    val playing: Boolean get() = AudioEngine.playing
    val loading: Boolean get() = AudioEngine.loading

    /**
     * Where the bar should sit while a seek settles.
     *
     * Asking the engine to move takes a moment — it has to fetch from the new
     * offset — and during that gap it keeps reporting the OLD position. Showing
     * that makes the bar snap backwards the instant you let go, which reads as
     * the seek having failed. Holding the requested position until the engine
     * agrees is what makes scrubbing feel solid.
     */
    private var seekTarget by mutableStateOf<Float?>(null)

    /** 0..1 through the current track, for the progress bar. */
    val progress: Float
        get() = seekTarget
            ?: if (AudioEngine.duration > 0) (AudioEngine.position / AudioEngine.duration).toFloat() else 0f

    /** Where playback actually is, in seconds. What the lyrics follow. */
    val positionSeconds: Double get() = AudioEngine.position

    /** Step forward or back by a few seconds, the way the arrow keys do. */
    fun nudge(seconds: Double) {
        val duration = AudioEngine.duration
        if (duration <= 0) return
        seekTo((AudioEngine.position + seconds).coerceIn(0.0, duration))
    }

    /** Jump to a moment rather than a proportion. */
    fun seekTo(seconds: Double) {
        val duration = AudioEngine.duration
        if (duration <= 0) return
        seek((seconds / duration).toFloat())
    }

    val elapsed: String
        get() = clock(seekTarget?.let { it * AudioEngine.duration } ?: AudioEngine.position)
    val total: String get() = if (AudioEngine.duration > 0) clock(AudioEngine.duration) else current?.duration ?: "0:00"

    var opening by mutableStateOf(false)
        private set

    /** Open a tile and play what's inside it. */
    fun open(card: Catalogue.Card) {
        opening = true
        failure = null
        scope.launch {
            Catalogue.open(card).fold(
                onSuccess = { tracks ->
                    opening = false
                    if (tracks.isEmpty()) failure = "Nothing to play in ${card.title}"
                    else play(tracks)
                },
                onFailure = {
                    opening = false
                    failure = "Couldn't open ${card.title}"
                },
            )
        }
    }

    /**
     * Play a shelf of songs from the one that was clicked.
     *
     * Clicking a song should leave the rest of the shelf lined up behind it —
     * playing it alone and then falling silent is the wrong end of the
     * expectation. Nothing needs fetching: a song card already carries
     * everything a queue entry holds.
     */
    fun playAll(cards: List<Catalogue.Card>, startAt: Int = 0) {
        val songs = cards.filter { it.kind == Catalogue.Kind.Song }
        if (songs.isEmpty()) return
        failure = null
        play(songs.map { it.asTrack() }, startAt)
    }

    /**
     * Play a song and keep going with what it leads to.
     *
     * The song first, then everything the catalogue says is like it — so a
     * radio starts with the thing you asked for rather than with something
     * chosen on its behalf.
     */
    fun startRadio(track: Track) {
        failure = null
        play(listOf(track), 0, "${track.title} radio")
        scope.launch {
            Catalogue.relatedTo(track.id).onSuccess { rest ->
                val more = rest.filterNot { it.id == track.id }
                if (more.isNotEmpty() && current?.id == track.id) queue = queue + more
            }
        }
    }

    /** The same songs in a different order, starting from the top of it. */
    fun shuffle(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        failure = null
        play(tracks.shuffled())
    }

    /**
     * Where the queue came from — an album, a playlist, a radio.
     *
     * Worth keeping because a queue with no name is a list of songs you have to
     * read to recognise. "Playing from Ahista Ahista Mix" is the difference
     * between knowing what you put on and working it out from track four.
     */
    var playingFrom by mutableStateOf<String?>(null)
        private set

    fun play(tracks: List<Track>, startAt: Int = 0) = play(tracks, startAt, null)

    fun play(tracks: List<Track>, startAt: Int, from: String?) {
        playingFrom = from
        queue = tracks
        ordered = tracks
        index = startAt.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        if (shuffling) reshuffle()
        start()
    }

    /**
     * What repeating at the end of the queue does.
     *
     * [One] is about the track, the others about the list — which is why they
     * are one setting rather than two: no one means "repeat this song, and also
     * loop the album afterwards".
     */
    enum class Repeat { Off, All, One }

    var repeat by mutableStateOf(Repeat.Off)
        private set

    fun cycleRepeat() {
        repeat = when (repeat) {
            Repeat.Off -> Repeat.All
            Repeat.All -> Repeat.One
            Repeat.One -> Repeat.Off
        }
    }

    var shuffling by mutableStateOf(false)
        private set

    /** The queue as it was handed over, so shuffling can be undone. */
    private var ordered: List<Track> = emptyList()

    /**
     * Shuffle, or put it back.
     *
     * Turning it on leaves the song that's playing exactly where it is and
     * shuffles what hasn't been reached — jumping to a different track the
     * moment you press shuffle is a jarring thing to do to someone mid-song.
     * Turning it off restores the order it arrived in and finds the current
     * track's place in that.
     */
    fun toggleShuffle() {
        shuffling = !shuffling
        if (queue.isEmpty()) return
        if (shuffling) reshuffle() else {
            val playing = current
            queue = ordered
            playing?.let { index = queue.indexOf(it).coerceAtLeast(0) }
        }
    }

    private fun reshuffle() {
        val playing = current ?: return
        val rest = queue.filterNot { it === playing }.shuffled()
        queue = listOf(playing) + rest
        index = 0
    }

    /**
     * Put a song straight after the one playing.
     *
     * Anything already in the queue is moved rather than duplicated — asking
     * for a song next when it's further down the list means "sooner", not
     * "twice".
     */
    fun playNext(track: Track) {
        if (queue.isEmpty()) { play(listOf(track)); return }
        val without = queue.filterNot { it.id == track.id }
        val at = without.indexOf(current).coerceAtLeast(0)
        queue = without.toMutableList().also { it.add(at + 1, track) }
        index = at
    }

    /** Put a song at the end of the queue. */
    fun addToQueue(track: Track) {
        if (queue.isEmpty()) { play(listOf(track)); return }
        if (queue.any { it.id == track.id }) return
        queue = queue + track
    }

    fun toggle() {
        if (current == null) return
        // Nothing loaded yet — the first press should start it, not toggle silence.
        if (AudioEngine.duration == 0.0 && !AudioEngine.loading) start() else AudioEngine.toggle()
        // Told after the fact rather than before, so what the room hears is
        // what actually happened here.
        Together.share(if (AudioEngine.playing) Did.PLAY else Did.PAUSE)
    }

    fun next() {
        if (index + 1 in queue.indices) { index += 1; start() }
    }

    fun previous() {
        // Restart the track first, the way every player does, and only step back
        // when you're already near the beginning.
        if (AudioEngine.position > 3.0) AudioEngine.seek(0.0)
        else if (index - 1 in queue.indices) { index -= 1; start() }
    }

    fun seek(fraction: Float) {
        val target = fraction.coerceIn(0f, 1f)
        seekTarget = target
        AudioEngine.seek(target.toDouble())
        Together.share(Did.SEEK)
        scope.launch {
            // Let go once the engine lands near where it was asked to go, or
            // give up after a moment so a failed seek can't freeze the bar.
            val deadline = System.currentTimeMillis() + 2000
            while (System.currentTimeMillis() < deadline) {
                delay(60)
                val duration = AudioEngine.duration
                if (duration <= 0) continue
                val now = (AudioEngine.position / duration).toFloat()
                if (kotlin.math.abs(now - target) < 0.01f) break
            }
            seekTarget = null
        }
    }

    /** Jump straight to a track in the queue. */
    fun jumpTo(position: Int) {
        if (position !in queue.indices || position == index) return
        index = position
        start()
    }

    /** Drop a track. Removing the one playing moves on to the next. */
    fun removeAt(position: Int) {
        if (position !in queue.indices) return
        val wasCurrent = position == index
        queue = queue.toMutableList().also { it.removeAt(position) }
        when {
            queue.isEmpty() -> { AudioEngine.stop(); index = 0 }
            wasCurrent -> { index = index.coerceAtMost(queue.lastIndex); start() }
            position < index -> index -= 1
        }
    }

    /** Reorder by dragging, keeping the playing track under the same finger. */
    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val playing = current
        queue = queue.toMutableList().also { it.add(to, it.removeAt(from)) }
        playing?.let { index = queue.indexOf(it).coerceAtLeast(0) }
    }

    var volume by mutableStateOf(0.8f)
        private set

    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        if (volume > 0f) beforeMute = volume
        AudioEngine.setVolume(volume.toDouble())
    }

    /** What to go back to when unmuting. */
    private var beforeMute = 0.8f

    fun toggleMute() {
        changeVolume(if (volume > 0f) 0f else beforeMute)
    }

    init {
        // Nothing else is watching the engine, so the queue would stall on the
        // first track without this.
        AudioEngine.onFinished = { advance() }
    }

    /**
     * Bring a song in rather than have it arrive.
     *
     * A short ramp on the way in, which is enough to stop a track landing like
     * a slammed door — and a longer one when a fade has been asked for. It sets
     * the volume back to where it was, never above it: the number on the slider
     * is the number that plays.
     */
    private fun fadeUp() {
        val seconds = when {
            Playback.fadeSeconds > 0f -> Playback.fadeSeconds
            Playback.easeIn -> 0.35f
            else -> return
        }
        val target = volume
        scope.launch {
            val steps = (seconds * 20).toInt().coerceIn(4, 160)
            AudioEngine.setVolume(0.0)
            repeat(steps) { step ->
                delay((seconds * 1000 / steps).toLong())
                AudioEngine.setVolume(target.toDouble() * (step + 1) / steps)
            }
            AudioEngine.setVolume(target.toDouble())
        }
    }

    /** True while the queue is being lengthened, so it is only done once. */
    private var extending = false

    /**
     * Carry on past the end of the queue.
     *
     * Built from the last thing played rather than the first, because what you
     * want next follows from what you just heard — an hour into a queue, the
     * song it started with is not the thread any more.
     */
    private fun extend() {
        if (extending) return
        val seed = queue.lastOrNull() ?: return
        extending = true
        scope.launch {
            Catalogue.relatedTo(seed.id).onSuccess { more ->
                val fresh = more.filterNot { song -> queue.any { it.id == song.id } }
                if (fresh.isNotEmpty()) {
                    queue = queue + fresh
                    ordered = queue
                    index += 1
                    start()
                }
            }
            extending = false
        }
    }

    /**
     * What happens when a track runs out on its own.
     *
     * Distinct from pressing next: reaching the end of a song is when repeat
     * gets a say, and pressing skip on the last track should still stop rather
     * than silently start the album again.
     */
    private fun advance() {
        // Asked before anything else: a timer set to end here means the queue
        // stops, whatever repeat would otherwise have done.
        if (SleepTimer.consumeTrackEnd()) return
        when {
            repeat == Repeat.One -> start()
            index + 1 in queue.indices -> next()
            repeat == Repeat.All && queue.isNotEmpty() -> { index = 0; start() }

            // Nothing left and nothing repeating. Rather than stop dead, keep
            // going from where the queue ended up — silence at the end of an
            // album is a decision the album made, not one you made.
            Playback.keepGoing && queue.isNotEmpty() -> extend()
        }
    }

    private fun start() {
        val track = current ?: return
        // Everyone in the room hears what the host started, and hears it from
        // their own copy — the wire carries which song and where in it, never
        // the audio.
        Together.share(Did.CHANGE_TRACK)
        failure = null
        seekTarget = null

        // The words are fetched now rather than when the panel is opened, and
        // the next song's are fetched with them. Several services asked over
        // the network take a few seconds however well it's done — the fix is
        // for those seconds to happen while the song is starting instead of
        // while somebody is staring at an empty panel.
        LyricsSource.warm(track)
        // The next two, not just the next one. Skipping twice in a row is
        // ordinary, and the point of fetching ahead is that the panel is never
        // the thing being waited for.
        LyricsSource.warm(queue.getOrNull(index + 1))
        LyricsSource.warm(queue.getOrNull(index + 2))
        Scrobbler.began(track)
        if (!AudioEngine.available()) {
            failure = "Audio support is missing — install VLC and restart Blazify"
            return
        }
        // Anything already on disk needs no resolving — hand the path straight
        // over. A kept copy is preferred to the network even when there is one:
        // it starts sooner and can't stall halfway through.
        val onDisk = when {
            LocalMusic.isLocal(track.id) -> LocalMusic.pathOf(track.id)
            Downloads.has(track.id) -> Downloads.fileFor(track.id).absolutePath
            else -> null
        }
        if (onDisk != null) {
            AudioEngine.play(onDisk)
            fadeUp()
            Library.played(track)
            return
        }

        scope.launch {
            Catalogue.streamUrl(track.id).fold(
                onSuccess = {
                    AudioEngine.play(it)
                    fadeUp()
                    // Recorded once it's actually playing rather than on the
                    // click, so a track that never resolves doesn't leave a
                    // false entry in a list of what you listened to.
                    Library.played(track)
                },
                onFailure = {
                    failure = "Couldn't play ${track.title}"
                    // Sitting on a song that will not play, waiting to be
                    // rescued, is the worst of the options — so move on unless
                    // that was asked for.
                    if (Playback.skipBroken && index + 1 in queue.indices) next()
                },
            )
        }
    }

    /** Keep whatever is playing, so it works without the network. */
    fun downloadCurrent() {
        current?.let { Downloads.start(it) }
    }

    /** Like or unlike whatever is playing. */
    fun toggleLike() {
        current?.let { Library.toggleLike(it) }
    }

    val currentLiked: Boolean get() = current?.let { Library.isLiked(it.id) } == true

    private fun clock(seconds: Double): String {
        val whole = seconds.toInt().coerceAtLeast(0)
        return "%d:%02d".format(whole / 60, whole % 60)
    }
}
