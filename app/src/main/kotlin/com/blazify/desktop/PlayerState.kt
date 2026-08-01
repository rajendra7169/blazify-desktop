package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.Catalogue
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

    /** The same songs in a different order, starting from the top of it. */
    fun shuffle(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        failure = null
        play(tracks.shuffled())
    }

    fun play(tracks: List<Track>, startAt: Int = 0) {
        queue = tracks
        index = startAt.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        start()
    }

    fun toggle() {
        if (current == null) return
        // Nothing loaded yet — the first press should start it, not toggle silence.
        if (AudioEngine.duration == 0.0 && !AudioEngine.loading) start() else AudioEngine.toggle()
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
        AudioEngine.setVolume(volume.toDouble())
    }

    init {
        // Nothing else is watching the engine, so the queue would stall on the
        // first track without this.
        AudioEngine.onFinished = { next() }
    }

    private fun start() {
        val track = current ?: return
        failure = null
        seekTarget = null
        if (!AudioEngine.available()) {
            failure = "Audio support is missing — install VLC and restart Blazify"
            return
        }
        scope.launch {
            Catalogue.streamUrl(track.id).fold(
                onSuccess = { AudioEngine.play(it) },
                onFailure = { failure = "Couldn't play ${track.title}" },
            )
        }
    }

    private fun clock(seconds: Double): String {
        val whole = seconds.toInt().coerceAtLeast(0)
        return "%d:%02d".format(whole / 60, whole % 60)
    }
}
