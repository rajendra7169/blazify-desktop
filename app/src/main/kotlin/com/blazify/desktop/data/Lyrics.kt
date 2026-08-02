package com.blazify.desktop.data

import com.blazify.desktop.ui.Look
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
 * Every source is asked **at once**, not one after another. Asked in turn, the
 * wait is the sum of everything that had nothing — and the sources that answer
 * fastest are rarely the ones with the best words, so the sheet either arrived
 * late or arrived worse. Asked together, the wait is the slowest single source
 * and the order in Settings still decides who wins.
 *
 * A timed transcript beats a flat one from a source above it, since a sheet
 * that cannot follow the song is barely a sheet. Beyond that the order is
 * exactly the order you set.
 *
 * Results are held for the session, and fetched before the panel is opened —
 * see [warm]. Between the two, opening the words is normally instant.
 */
object LyricsSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = mutableMapOf<String, Lyrics>()
    private val working = mutableSetOf<String>()

    /**
     * Which source answered, per song.
     *
     * Kept beside the words rather than as "the last one that answered": two
     * panels and a change of track would otherwise credit whichever lookup
     * finished most recently, which is how a page ends up naming a source it
     * never used.
     */
    private val credits = mutableMapOf<String, String>()

    fun creditFor(id: String): String? = credits[id]

    /**
     * A source chosen by hand for one song.
     *
     * Per song rather than a setting, because this is not a preference — it is
     * "these are the wrong words for this track", which is about the track. It
     * is dropped when the app closes for the same reason.
     */
    private val chosen = mutableStateMapOf<String, String>()

    fun preferenceFor(id: String): String? = chosen[id]

    fun prefer(id: String, provider: String?) {
        if (provider == null) chosen.remove(id) else chosen[id] = provider
        cache.remove(id)
        credits.remove(id)
    }

    /** Forget one song, so the next look asks again. */
    fun forget(id: String) {
        cache.remove(id)
        credits.remove(id)
    }

    /**
     * Fetch the words before anybody asks for them.
     *
     * Called when a song starts and again for whatever is queued next, so
     * opening the panel shows a sheet rather than a spinner. Costs nothing when
     * the panel is never opened — a handful of requests against services that
     * would have been asked anyway.
     */
    fun warm(track: Track?) {
        val id = track?.id ?: return
        if (id in cache || id in working) return
        scope.launch { of(track) }
    }

    suspend fun of(track: Track): Lyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        // One source, named for this song, and no falling back to the others:
        // asking for LrcLib and quietly being given KuGou is worse than being
        // told LrcLib hasn't got it.
        val only = chosen[track.id]?.let { LyricsProviders.byName(it) }
        if (only != null) {
            val raw = runCatching { only.find(track) }.getOrNull()
            val words = if (raw.isNullOrBlank()) Lyrics() else read(raw)
            if (!words.empty) credits[track.id] = only.name
            cache[track.id] = words
            return@withContext words
        }

        synchronized(working) { working += track.id }
        try {
            val chain = Look.lyricsChain()
            val answers = coroutineScope {
                chain.map { provider ->
                    async {
                        // One source throwing must never stop the rest, and no
                        // words at all is an ordinary outcome rather than a
                        // failure worth reporting.
                        val raw = runCatching { provider.find(track) }.getOrNull()
                        provider.name to (raw?.takeIf { it.isNotBlank() }?.let(::read) ?: Lyrics())
                    }
                }.awaitAll()
            }.filterNot { it.second.empty }

            // The order set in Settings decides, except that a timed transcript
            // beats a flat one — following along is the whole point, and a
            // preferred source with only a flat copy hasn't really answered.
            val best = answers.minByOrNull { (name, words) ->
                val place = chain.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: chain.size
                place + if (words.synced) 0 else chain.size + 1
            }

            val found = best?.second ?: Lyrics()
            best?.first?.let { credits[track.id] = it }
            cache[track.id] = found
            found
        } finally {
            synchronized(working) { working -= track.id }
        }
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
     * verses is part of following along.
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
