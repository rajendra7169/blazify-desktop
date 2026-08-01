package com.blazify.desktop.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 * A public lyrics service, matched on title, artist and length. Length is what
 * separates a studio cut from a live take of the same song, so it's sent when
 * we know it — the match is noticeably better with it than without.
 *
 * Results are held for the session. Looking the same song up twice in one
 * sitting is common (skip back, reopen the panel) and none of it changes
 * between one look and the next.
 */
object LyricsSource {

    private val client by lazy { HttpClient(OkHttp) }
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Lyrics>()

    suspend fun of(track: Track): Lyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        val found = try {
            val body = client.get("https://lrclib.net/api/get") {
                parameter("track_name", track.title.cleaned())
                parameter("artist_name", track.artist.substringBefore(",").trim())
                track.durationSeconds?.let { parameter("duration", it) }
            }.bodyAsText()

            val fields = json.parseToJsonElement(body).jsonObject
            val timed = fields["syncedLyrics"]?.jsonPrimitive?.contentOrNullSafe()
            val flat = fields["plainLyrics"]?.jsonPrimitive?.contentOrNullSafe()
            Lyrics(lines = timed?.let(::parse).orEmpty(), plain = flat)
        } catch (_: Exception) {
            // No words is an ordinary outcome, not a failure worth reporting.
            Lyrics()
        }

        cache[track.id] = found
        found
    }

    /**
     * Strip the decoration catalogues hang off titles.
     *
     * "Kesariya (From "Brahmastra")" won't match anything; "Kesariya" will.
     */
    private fun String.cleaned() = replace(Regex("\\s*[\\(\\[][^)\\]]*[\\)\\]]"), "").trim()
        .ifEmpty { this }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }

    /**
     * Read a timed transcript.
     *
     * Every line is stamped `[mm:ss.cc]`. Anything without a stamp is a header
     * the file carries about itself, and is skipped. Empty lines are kept: a
     * pause between verses is part of following along.
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
