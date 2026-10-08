package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** How far one song's words are moved, and which song. */
@Serializable
data class LyricsNudge(val id: String, val seconds: Double)

/**
 * Words that run a little early or late, put right for that song.
 *
 * Timings are written by people against one release of a track, and the copy
 * being played is often another — a radio edit with a shorter intro, a video
 * with a second of silence at the front. The whole sheet is out by the same
 * amount, so one number fixes it, and that number belongs to the song: the next
 * track's words are someone else's timing and are probably fine.
 *
 * Kept on disk, because having put a song right once it should stay right.
 */
object LyricsTiming {

    private const val FILE = "lyrics-timing.json"

    /** One press. Half a second is about a syllable — small enough to land on the beat, big enough to notice. */
    const val STEP = 0.5

    /** Beyond this the words are for a different recording, and no nudge will help. */
    private const val FURTHEST = 10.0

    private var all by mutableStateOf(Store.read<LyricsNudge>(FILE).associate { it.id to it.seconds })

    /**
     * How much sooner this song's words should come, in seconds.
     *
     * Positive brings each line in earlier, which is the fix for words that
     * trail behind the singing; negative holds them back.
     */
    fun of(id: String?): Double = id?.let { all[it] } ?: 0.0

    fun nudge(id: String, by: Double) = set(id, of(id) + by)

    fun reset(id: String) = set(id, 0.0)

    private fun set(id: String, seconds: Double) {
        // Rounded to a tenth so repeated presses never drift into 1.4999 s.
        val clean = ((seconds.coerceIn(-FURTHEST, FURTHEST)) * 10).roundToInt() / 10.0
        all = if (abs(clean) < 0.05) all - id else all + (id to clean)
        Store.write(FILE, all.map { (song, by) -> LyricsNudge(song, by) })
    }

    /** "+0.5 s", "−1.0 s", or "0 s" when nothing has been moved. */
    fun label(seconds: Double): String = when {
        abs(seconds) < 0.05 -> "0 s"
        seconds > 0 -> String.format(Locale.ROOT, "+%.1f s", seconds)
        else -> String.format(Locale.ROOT, "−%.1f s", -seconds)
    }
}
