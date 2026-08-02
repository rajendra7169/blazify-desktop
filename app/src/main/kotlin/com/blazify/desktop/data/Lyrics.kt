package com.blazify.desktop.data

import com.blazify.desktop.ui.Look
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** One line of a song, and the moment it is sung. */
data class LyricLine(val at: Double, val text: String)

/**
 * The words to a song.
 *
 * [lines] carry timings and can be followed along; [plain] is the fallback for
 * anything that only has a flat transcript. Both may be present — a synced set
 * is always preferred, and the plain text is kept so a song with only that
 * still shows something.
 */
data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val plain: String? = null,
) {
    val synced: Boolean get() = lines.isNotEmpty()
    val empty: Boolean get() = lines.isEmpty() && plain.isNullOrBlank()

    /**
     * Which line is being sung at [seconds].
     *
     * Returns -1 before the first line, which is the intro and shouldn't
     * highlight anything.
     */
    fun lineAt(seconds: Double): Int {
        if (lines.isEmpty()) return -1
        var found = -1
        for ((at, line) in lines.withIndex()) {
            if (line.at <= seconds) found = at else break
        }
        return found
    }
}

/**
 * Where the words come from.
 *
 * Several services, asked in the order you set, stopping at the first that
 * answers. A timed transcript is what everyone actually wants, so a source that
 * returns one ends the search — but a source returning only flat text does not:
 * the plain copy is held and the list carries on, in case something further
 * down has it with timings. Better a slower answer than a page that can't
 * follow the song.
 *
 * Results are held for the session. Looking the same song up twice in one
 * sitting is common — skip back, reopen the panel — and none of it changes
 * between one look and the next.
 */
object LyricsSource {

    private val cache = mutableMapOf<String, Lyrics>()

    /** Which source answered for the song now showing, for the panel to name. */
    var attribution: String? = null
        private set

    /** Forget one song, so the next look asks again. */
    fun forget(id: String) {
        cache.remove(id)
    }

    suspend fun of(track: Track): Lyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let {
            attribution = null
            return@withContext it
        }

        var flat: Lyrics? = null
        var flatFrom: String? = null
        var found = Lyrics()
        var from: String? = null

        for (provider in Look.lyricsChain()) {
            // No words is an ordinary outcome, not a failure worth reporting —
            // and one source throwing must never stop the rest being asked.
            val raw = runCatching { provider.find(track) }.getOrNull()
            if (raw.isNullOrBlank()) continue

            val parsed = read(raw)
            if (parsed.synced) {
                found = parsed
                from = provider.name
                break
            }
            if (flat == null && !parsed.plain.isNullOrBlank()) {
                flat = parsed
                flatFrom = provider.name
            }
        }

        if (!found.synced && flat != null) {
            found = flat
            from = flatFrom
        }

        attribution = from
        cache[track.id] = found
        found
    }

    /**
     * Read whatever a source handed back.
     *
     * Stamped lines become a timed transcript; anything without a stamp is
     * either a header the file carries about itself or a flat transcript, and
     * which of the two it is depends on whether there were any stamps at all.
     */
    fun read(text: String): Lyrics {
        val lines = parse(text)
        return if (lines.isNotEmpty()) {
            Lyrics(lines = lines, plain = null)
        } else {
            Lyrics(plain = text.trim().takeIf { it.isNotBlank() })
        }
    }

    /**
     * Read a timed transcript.
     *
     * Every line is stamped `[mm:ss.cc]`. Empty lines are kept: a pause between
     * verses is part of following along. A stamp with nothing after it and no
     * further stamps is metadata, not a lyric.
     */
    private fun parse(text: String): List<LyricLine> {
        val stamp = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")
        return text.lineSequence().mapNotNull { line ->
            val match = stamp.find(line) ?: return@mapNotNull null
            val (minutes, seconds, fraction) = match.destructured
            val hundredths = fraction.padEnd(2, '0').take(2).toIntOrNull() ?: 0
            LyricLine(
                at = minutes.toInt() * 60 + seconds.toInt() + hundredths / 100.0,
                text = line.substring(match.range.last + 1).trim(),
            )
        }.sortedBy { it.at }.toList()
    }
}
