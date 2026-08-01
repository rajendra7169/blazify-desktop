package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** 0..1 through the current track, for the progress bar. */
    val progress: Float
        get() = if (AudioEngine.duration > 0) (AudioEngine.position / AudioEngine.duration).toFloat() else 0f

    val elapsed: String get() = clock(AudioEngine.position)
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

    fun seek(fraction: Float) = AudioEngine.seek(fraction.toDouble())

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
