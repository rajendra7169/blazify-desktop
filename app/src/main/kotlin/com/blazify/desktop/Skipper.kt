package com.blazify.desktop

import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.audio.Silence
import com.blazify.desktop.data.Cache
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Store
import com.blazify.desktop.data.Listening
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.SponsorBlock
import com.blazify.desktop.data.Track
import com.blazify.desktop.together.Together
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Jumps over the parts of a song nobody chose to hear.
 *
 * Told about each song as it starts, it gathers the stretches worth passing —
 * the ones viewers marked in a music video, and long quiet ones in a song on
 * disk — and watches the clock. When
 * playback walks into one it moves to the far side; when the stretch runs to
 * the end of the song, the song is treated as finished, so a repeat or a count
 * still has its say.
 *
 * Each stretch is jumped once per play. Somebody who drags back into the
 * middle of one wants to hear it, and pulling them out again would be a fight
 * over the bar.
 */
object Skipper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * One stretch to pass, and what marked it.
     *
     * [length] is how long the recording was when it was marked, or zero when
     * that isn't known.
     */
    data class Stretch(val from: Double, val to: Double, val why: String, val length: Double = 0.0)

    @Volatile private var trackId: String? = null
    @Volatile private var stretches: List<Stretch> = emptyList()
    private val passed = mutableSetOf<Stretch>()
    private var gathering: Job? = null

    init {
        scope.launch {
            while (isActive) {
                delay(150)
                runCatching { watch() }
            }
        }
    }

    /**
     * A new song, or the same one again from the top.
     *
     * Whatever was gathered for the last one goes, and the asking for this one
     * happens in the background: the song has already started, and nothing
     * here is allowed to hold it up.
     */
    fun began(track: Track) {
        gathering?.cancel()
        synchronized(passed) { passed.clear() }
        if (trackId == track.id && stretches.isNotEmpty()) return
        trackId = track.id
        stretches = emptyList()
        gathering = scope.launch {
            val kinds = Listening.skipKinds
            // A feed's episodes are not videos, and a file on this machine has
            // no entry in anybody's database.
            if (kinds.isNotEmpty() && track.stream == null && !LocalMusic.isLocal(track.id)) {
                val marked = SponsorBlock.segments(track.id, kinds)
                    .map { Stretch(it.from, it.to, it.kind, it.length) }
                add(track.id, marked)
            }
        }
        if (Listening.skipSilence) {
            hushes(track)
            // The next one too, while this plays, so its first second of
            // quiet is known before it starts rather than found during it.
            PlayerState.queue.getOrNull(PlayerState.index + 1)?.let { next ->
                if (next.id != track.id) scope.launch { quietIn(next) }
            }
        }
    }

    /** What was found in each song already measured, newest last. */
    private val measured = object : LinkedHashMap<String, List<Stretch>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Stretch>>) = size > 64
    }

    private fun hushes(track: Track) {
        scope.launch {
            val found = quietIn(track) ?: return@launch
            add(track.id, found)
        }
    }

    /**
     * The quiet stretches in a song on disk, measured once and remembered.
     *
     * Null for a song that is only a stream: reading it would mean fetching it
     * all a second time beside the copy that is playing.
     */
    private fun quietIn(track: Track): List<Stretch>? {
        synchronized(measured) { measured[track.id] }?.let { return it }
        val file = onDisk(track) ?: return null
        val work = File(Store.folder, "silence-work/${track.id.hashCode().toUInt()}.wav")
        val found = Silence.find(file, work).map { Stretch(it.from, it.to, "silence") }
        synchronized(measured) { measured[track.id] = found }
        return found
    }

    private fun onDisk(track: Track): File? = when {
        LocalMusic.isLocal(track.id) -> File(LocalMusic.pathOf(track.id))
        Downloads.has(track.id) -> Downloads.fileFor(track.id)
        Cache.has(track.id) -> Cache.fileFor(track.id)
        else -> null
    }?.takeIf { it.isFile }

    /** More stretches for this song, from whoever found them. */
    fun add(id: String, more: List<Stretch>) {
        if (more.isEmpty() || trackId != id) return
        stretches = (stretches + more).sortedBy { it.from }
    }

    private fun watch() {
        val id = trackId ?: return
        val now = stretches
        if (now.isEmpty()) return
        if (PlayerState.current?.id != id) return
        if (!AudioEngine.playing || AudioEngine.buffering || AudioEngine.loading) return
        // A guest in a room hears what the host hears. The host's own player
        // does the skipping, and the room follows it.
        if (Together.code != null && !Together.hosting) return

        val duration = AudioEngine.duration
        if (duration <= 0) return
        val at = AudioEngine.position
        val inside = now.firstOrNull { stretch ->
            // Marked on a different cut of the recording: every time in it
            // is somewhere else in this one.
            val sameCut = stretch.length <= 0 || kotlin.math.abs(stretch.length - duration) < 3.0
            sameCut && at >= stretch.from && at < stretch.to - 0.4
        } ?: return
        val fresh = synchronized(passed) { passed.add(inside) }
        if (!fresh) return

        if (inside.to >= duration - 1.0) {
            // Nothing after it but the end. Jumping to the last instant can
            // leave the player sitting there, so the song is finished instead.
            PlayerState.endEarly()
        } else {
            PlayerState.seekTo(inside.to)
        }
    }
}
