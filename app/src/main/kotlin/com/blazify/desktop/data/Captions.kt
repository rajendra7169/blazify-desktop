package com.blazify.desktop.data

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What was said, where the people who made it published it.
 *
 * An hour of talk has no lyrics and never will, so the words panel is empty on
 * exactly the thing it would help with most — following an interview in a
 * second language, finding the part somebody mentioned, reading along in a
 * noisy room.
 *
 * Nothing here transcribes anything, and the obvious way to get that for free
 * turned out to be closed. The catalogue does run its own recognition and does
 * list the result on the page, but the address it gives out now answers every
 * request with two hundred and nothing at all — measured three ways and
 * through four different clients, none of which are handed the tracks any
 * more. Getting past that needs a token minted by running their own code,
 * which is a browser engine's job and not this application's.
 *
 * What does work is the place feeds keep for this. A minority of shows fill it
 * in — two of fourteen, measured — and where they do it is the real thing:
 * written or checked by the people who made the programme rather than a
 * machine's guess. So this offers exactly that, and stays quiet elsewhere
 * rather than pretending.
 */
object Captions : LyricsProvider {

    override val name = "Transcript"

    override suspend fun find(track: Track): String? {
        // Only what the feed already told us about. Nothing is searched for:
        // there is no directory of transcripts to search.
        val where = track.words?.takeIf { track.talk } ?: return null

        val body = runCatching {
            LyricsProviders.http.get(where) {
                header("User-Agent", AGENT)
            }.bodyAsText()
        }.getOrNull()?.takeIf { it.isNotBlank() }
            // An hour of speech is perhaps eighty thousand characters. Past a
            // megabyte this is not a transcript, it is a page that went wrong,
            // and no amount of it is worth chewing through.
            ?.take(1_200_000)
            ?: return null

        return when {
            // Subtitles, which are already a list of moments with words on
            // them — the same thing a lyric file is, punctuated differently.
            "-->" in body -> fromSubtitles(body)
            // A page, which is words and no timings. Still worth having: the
            // panel shows it as a block instead of following along, which is
            // how reading a transcript works anyway.
            else -> asPlainWords(body)
        }
    }

    /**
     * Subtitles as timed lines.
     *
     * Both of the common shapes at once — the one with numbered blocks and the
     * one without — because they differ in the parts being thrown away rather
     * than in the parts being kept.
     */
    private fun fromSubtitles(body: String): String? {
        val lines = mutableListOf<String>()
        var at: String? = null
        val words = StringBuilder()

        fun flush() {
            val moment = at ?: return
            val said = words.toString().trim()
            if (said.isNotBlank()) lines += "[$moment]$said"
            words.clear()
        }

        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                "-->" in line -> {
                    flush()
                    at = stamp(line.substringBefore("-->").trim())
                }
                line.isBlank() -> flush().also { at = null }
                // The block numbers a subtitle file uses to count itself.
                line.toIntOrNull() != null && words.isEmpty() -> Unit
                line.startsWith("WEBVTT") -> Unit
                else -> words.append(if (words.isEmpty()) "" else " ").append(strip(line))
            }
        }
        flush()
        return lines.takeIf { it.size > 4 }?.joinToString("\n")
    }

    /** A moment, in the form the rest of the app reads. */
    private fun stamp(written: String): String? {
        val parts = written.replace(",", ".").split(":")
        val seconds = parts.lastOrNull()?.toDoubleOrNull() ?: return null
        val minutes = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0
        val hours = parts.getOrNull(parts.size - 3)?.toIntOrNull() ?: 0
        val total = hours * 3600 + minutes * 60 + seconds
        return "%02d:%02d.%02d".format(
            (total / 60).toInt(),
            (total % 60).toInt(),
            ((total % 1) * 100).toInt(),
        )
    }

    /**
     * A page of transcript, with the page taken off it.
     *
     * The scripts and styles are cut out by walking the text rather than by
     * matching across it. A pattern that has to find the end of a block can be
     * made to backtrack for minutes on a large page — it hung on the first one
     * this was tried against — and the walk cannot, because it only ever moves
     * forwards.
     */
    private fun asPlainWords(body: String): String? = withoutBlocks(body)
        .replace(Regex("</(p|div|h[1-6]|li)>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]{0,400}>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
        .takeIf { it.length > 200 }

    /** Cut out everything between an opening tag and its close, in one pass. */
    private fun withoutBlocks(body: String): String {
        var text = body
        for (tag in listOf("script", "style")) {
            val out = StringBuilder(text.length)
            var at = 0
            while (true) {
                val start = text.indexOf("<$tag", at, ignoreCase = true)
                if (start < 0) { out.append(text, at, text.length); break }
                out.append(text, at, start)
                val end = text.indexOf("</$tag>", start, ignoreCase = true)
                if (end < 0) break
                at = end + tag.length + 3
            }
            text = out.toString()
        }
        return text
    }

    private fun strip(line: String) = line.replace(Regex("<[^>]{0,200}>"), "").trim()

    private const val AGENT = "Blazify/1.0 (+podcast client)"
}
