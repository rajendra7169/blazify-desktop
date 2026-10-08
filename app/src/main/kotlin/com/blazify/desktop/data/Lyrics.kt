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

/**
 * One word of a line, and the stretch of time it is sung over.
 *
 * [text] keeps the space after it, if there was one, so the words of a line
 * joined back together are exactly the line — a syllable that runs straight
 * into the next one has no space, and gluing on a space of our own would
 * split words in half.
 */
data class LyricWord(val at: Double, val end: Double, val text: String)

/**
 * One line of a song, and the moment it is sung.
 *
 * [words] is empty for a source that only times whole lines, which is most of
 * them. When it is filled, the words joined together are exactly [text].
 */
data class LyricLine(
    val at: Double,
    val text: String,
    val words: List<LyricWord> = emptyList(),
) {
    val timedWords: Boolean get() = words.isNotEmpty()
}

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

    /**
     * The words as a timed transcript, the way they arrived.
     *
     * Word timings are written as `<mm:ss.cc>` marks inside each line, which is
     * the form every karaoke-aware player reads and every other one skips, so
     * nothing is lost by keeping them and nothing breaks for a reader that
     * doesn't know them.
     */
    fun toLrc(): String = if (synced) {
        lines.joinToString("\n") { line ->
            if (line.words.isEmpty()) {
                lrcStamp(line.at) + line.text
            } else {
                lrcStamp(line.at) + line.words.joinToString("") { lrcStamp(it.at, word = true) + it.text } +
                    lrcStamp(line.words.last().end, word = true)
            }
        }
    } else {
        plain.orEmpty()
    }
}

/**
 * A moment as an LRC stamp: `[mm:ss.cc]`, or `<mm:ss.cc>` for a word.
 *
 * Formatted without the machine's locale. A language whose digits aren't the
 * Latin ones would otherwise write stamps no reader — this one included — can
 * read back.
 */
fun lrcStamp(seconds: Double, word: Boolean = false): String {
    val hundredths = Math.round(seconds.coerceAtLeast(0.0) * 100)
    val text = String.format(
        java.util.Locale.ROOT, "%02d:%02d.%02d",
        hundredths / 6000, (hundredths / 100) % 60, hundredths % 100,
    )
    return if (word) "<$text>" else "[$text]"
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
        versions[id] = (versions[id] ?: 0) + 1
    }

    /**
     * Bumped whenever a song's words change underneath an open sheet.
     *
     * The sheet looks its words up once per song; reading this alongside the
     * song tells it to look again after you have saved a timing of your own,
     * rather than going on showing the copy from before.
     */
    private val versions = mutableStateMapOf<String, Int>()

    fun versionOf(id: String): Int = versions[id] ?: 0

    // ── timings you made ─────────────────────────────────────────────────────

    /** Credited on the sheet, so it's clear whose timing this is. */
    const val SYNCED_BY_YOU = "Synced by you"

    private val own by lazy { java.io.File(Store.folder, "synced-lyrics").apply { mkdirs() } }

    /**
     * Where one song's own timing lives.
     *
     * A song from the catalogue has an id that is already a safe file name; a
     * song from a folder on this machine may not, so anything unusual is
     * replaced and a hash keeps two such names from landing on one file.
     */
    private fun ownFor(id: String): java.io.File {
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val name = if (safe == id) id else "${safe.take(60)}-${Integer.toHexString(id.hashCode())}"
        return java.io.File(own, "$name.lrc")
    }

    fun hasOwn(id: String): Boolean = ownFor(id).exists()

    /**
     * Keep a timing you made for a song whose words had none.
     *
     * Wins over every source from then on, since it is the one timing that
     * was made against exactly the recording you play.
     */
    fun keepOwn(id: String, lrc: String) {
        runCatching { ownFor(id).writeText(lrc) }
        forget(id)
    }

    /** Back to whatever the sources say. */
    fun forgetOwn(id: String) {
        runCatching { ownFor(id).delete() }
        forget(id)
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

        // Your own timing first: it was made against this very recording, and
        // it only exists because every source had nothing better.
        if (chosen[track.id] == null && hasOwn(track.id)) {
            val saved = runCatching { ownFor(track.id).readText() }.getOrNull()
            if (!saved.isNullOrBlank()) {
                val words = read(saved)
                if (!words.empty) {
                    credits[track.id] = SYNCED_BY_YOU
                    cache[track.id] = words
                    return@withContext words
                }
            }
        }

        // A kept song keeps its words too. Read before anything is asked over
        // the network, so a sheet for something on disk appears instantly and
        // appears at all when there is nothing to ask.
        if (chosen[track.id] == null && Offline.hasWords(track.id)) {
            val saved = runCatching { Offline.wordsFor(track.id).readText() }.getOrNull()
            if (!saved.isNullOrBlank()) {
                val words = read(saved)
                if (!words.empty) {
                    credits[track.id] = "Kept on this computer"
                    cache[track.id] = words
                    return@withContext words
                }
            }
        }

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
     * verses is part of following along. A line may also carry `<mm:ss.cc>`
     * marks before its words, which time each word; those are kept too, and a
     * line without them behaves exactly as one always has.
     */
    private fun parse(text: String): List<LyricLine> {
        val stamp = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")
        val lines = text.lineSequence().mapNotNull { line ->
            val match = stamp.find(line) ?: return@mapNotNull null
            val at = clock(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            val rest = line.substring(match.range.last + 1)
            val words = words(rest)
            LyricLine(
                at = at,
                text = if (words.isEmpty()) rest.replace(wordMark, "").trim() else words.joinToString("") { it.text },
                words = words,
            )
        }.sortedBy { it.at }.toList()

        // A word with no mark after it ends where the next line begins, or a
        // couple of seconds on for the last line of the song.
        return lines.mapIndexed { index, line ->
            if (line.words.isEmpty()) return@mapIndexed line
            val after = lines.getOrNull(index + 1)?.at ?: (line.words.last().at + 2.0)
            line.copy(
                words = line.words.map { word ->
                    if (word.end.isNaN()) word.copy(end = maxOf(after, word.at)) else word
                },
            )
        }
    }

    private val wordMark = Regex("<(\\d+):(\\d+)(?:[.:](\\d+))?>")

    /**
     * The words of one line, if it says when each is sung.
     *
     * Each mark starts a word and ends the one before it; a mark with nothing
     * after it is only there to end the last word. An end not yet known is
     * left as NaN for [parse] to fill in from the next line.
     */
    private fun words(rest: String): List<LyricWord> {
        val marks = wordMark.findAll(rest).toList()
        if (marks.isEmpty()) return emptyList()
        val raw = marks.mapIndexed { index, mark ->
            val at = clock(mark.groupValues[1], mark.groupValues[2], mark.groupValues[3])
            val next = marks.getOrNull(index + 1)
            val text = rest.substring(mark.range.last + 1, next?.range?.first ?: rest.length)
            val end = next?.let { clock(it.groupValues[1], it.groupValues[2], it.groupValues[3]) } ?: Double.NaN
            LyricWord(at, end, text)
        }
        // A piece that is only a space belongs to the word before it — it is
        // the gap between two words, not a word — and an empty one carries a
        // time but no letters, so there is nothing to light up.
        val merged = mutableListOf<LyricWord>()
        for (word in raw) {
            if (word.text.isBlank()) {
                if (word.text.isNotEmpty() && merged.isNotEmpty()) {
                    merged[merged.lastIndex] = merged.last().let { it.copy(text = it.text + word.text) }
                }
                continue
            }
            merged += word
        }
        if (merged.isEmpty()) return emptyList()
        // Trimmed at the edges only, so the joined words are the line as it
        // reads.
        merged[0] = merged[0].let { it.copy(text = it.text.trimStart()) }
        merged[merged.lastIndex] = merged.last().let { it.copy(text = it.text.trimEnd()) }
        return merged
    }

    private fun clock(minutes: String, seconds: String, fraction: String): Double {
        val hundredths = fraction.padEnd(2, '0').take(2).toIntOrNull() ?: 0
        return minutes.toInt() * 60 + seconds.toInt() + hundredths / 100.0
    }
}
