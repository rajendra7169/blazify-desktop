package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** How far into something you had got, and when you stopped. */
@Serializable
data class Mark(
    val track: Track,
    val seconds: Double,
    val length: Double,
    val at: Long,
) {
    val fraction: Float get() = if (length > 0) (seconds / length).toFloat() else 0f

    val left: String
        get() {
            val remaining = (length - seconds).toInt().coerceAtLeast(0)
            val minutes = remaining / 60
            return when {
                minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min left"
                minutes > 0 -> "$minutes min left"
                else -> "nearly done"
            }
        }
}

/**
 * Where you had got to.
 *
 * An hour-long episode that begins at nought every time is an hour-long
 * episode you will not finish. Nothing here was remembered at all until now:
 * every long recording started from the beginning on every visit, which is
 * fine for a three-minute song and useless for anything else.
 *
 * So only long things are remembered. A short song resuming two thirds of the
 * way through is an irritation rather than a courtesy — you wanted to hear the
 * song, not the end of it. And a mark is dropped once it reaches the end,
 * because a finished thing that keeps offering to resume at its last minute is
 * offering the one part nobody wants.
 */
object Resume {

    private const val FILE = "resume.json"

    /**
     * Below this, starting over is what somebody meant anyway.
     *
     * Ten minutes: long enough to exclude every song that is a song, short
     * enough to catch an interview, a set, a mix or an episode.
     */
    private const val WORTH_REMEMBERING = 10 * 60

    /** Near enough to the end to call it finished. */
    private const val FINISHED = 0.98

    /** And near enough to the start that there is nothing to resume. */
    private const val BARELY_STARTED = 30

    var all by mutableStateOf(Store.read<Mark>(FILE))
        private set

    /** Where you had got to in this, if it is worth going back to. */
    fun mark(id: String): Mark? = all.firstOrNull { it.track.id == id }

    /** The things you are part of the way through, most recent first. */
    val unfinished: List<Mark> get() = all.sortedByDescending { it.at }

    /**
     * Note where playback has reached.
     *
     * Called often — every few seconds while something plays — so it does as
     * little as possible when there is nothing to record, and writes only when
     * the answer has actually changed.
     */
    fun note(track: Track, seconds: Double, length: Double) {
        if (length < WORTH_REMEMBERING) return

        // Finished, or barely begun: either way there is nothing to come back
        // to, and a stale mark would send somebody to the wrong minute.
        if (seconds >= length * FINISHED || seconds < BARELY_STARTED) {
            forget(track.id)
            return
        }

        val existing = mark(track.id)
        if (existing != null && kotlin.math.abs(existing.seconds - seconds) < 1) return

        all = listOf(Mark(track, seconds, length, Instant.now().toEpochMilli())) +
            all.filterNot { it.track.id == track.id }
        save()
    }

    fun forget(id: String) {
        if (all.none { it.track.id == id }) return
        all = all.filterNot { it.track.id == id }
        save()
    }

    fun forgetAll() {
        all = emptyList()
        save()
    }

    private fun save() = Store.write(FILE, all)
}
