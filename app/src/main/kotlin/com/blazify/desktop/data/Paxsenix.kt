package com.blazify.desktop.data

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Apple Music's lyrics, which are the word-by-word ones.
 *
 * Worth the trouble it takes to reach: Apple pays for hand-timed transcripts
 * where the other services rely on donated ones, so for anything in the charts
 * these are the closest to karaoke you can get — timed per syllable rather than
 * per line.
 *
 * Getting them is a three-step affair. Apple's catalogue needs a token, which
 * is only ever handed out to its own web player, so the token is read out of
 * that player the same way a browser would load it. The catalogue then gives a
 * song id, and a public relay turns that id into the words. Nothing here logs
 * into anything or needs an Apple account.
 */
object Paxsenix : LyricsProvider {
    override val name = "Apple Music"

    private const val CATALOGUE = "https://amp-api.music.apple.com/v1/catalog/us"
    private const val RELAY = "https://lyrics.paxsenix.org/apple-music/lyrics"

    // What Apple's own web player sends. Its catalogue answers on the strength
    // of these, and a request without them is refused however good the token.
    private const val AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0"

    override suspend fun find(track: Track): String? {
        val title = track.title.plainTitle()
        val artist = track.artist.firstNamed()

        // Title and artist first; the title alone as a fallback, because an
        // artist written differently on the two catalogues turns a good match
        // into no match at all.
        val queries = listOf("$title $artist", title).distinct()

        for (query in queries) {
            val hits = runCatching { search(query) }.getOrNull().orEmpty()
            val best = hits.bestFor(title, artist, track.durationSeconds) ?: continue
            val words = runCatching { words(best.id) }.getOrNull()
            if (!words.isNullOrBlank()) return words
        }
        return null
    }

    // ── the token ────────────────────────────────────────────────────────────

    private val gate = Mutex()

    @Volatile
    private var token: String? = null

    /**
     * The web player's own key, read from the web player.
     *
     * Its bundle name changes with every release, so the page is fetched to
     * find out what the bundle is currently called before the key can be read
     * out of it. Cached for the session and thrown away on a refusal, which is
     * what a rotated key looks like from here.
     */
    private suspend fun token(): String = gate.withLock {
        token?.let { return it }

        val page = LyricsProviders.http.get("https://beta.music.apple.com") {
            header("User-Agent", AGENT)
        }.bodyAsText()

        val bundle = Regex("""/assets/index~[^"']+?\.js""").find(page)?.value
            ?: error("Apple's player has changed shape")

        val script = LyricsProviders.http.get("https://beta.music.apple.com$bundle") {
            header("User-Agent", AGENT)
        }.bodyAsText()

        val found = Regex("""eyJ[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
            .find(script)?.value ?: error("No key in Apple's player")

        token = found
        found
    }

    // ── the catalogue ────────────────────────────────────────────────────────

    private data class Hit(
        val id: String,
        val title: String,
        val artist: String,
        val seconds: Int?,
    )

    private suspend fun search(query: String): List<Hit> {
        val first = runCatching { ask(token(), query) }
        if (first.isSuccess) return first.getOrThrow()

        // A refused key is the ordinary way this fails — Apple rotates them.
        // One retry with a fresh one, then give up rather than loop.
        token = null
        return runCatching { ask(token(), query) }.getOrDefault(emptyList())
    }

    private suspend fun ask(key: String, query: String): List<Hit> {
        val term = URLEncoder.encode(query, "UTF-8")
        val body = LyricsProviders.http.get(
            "$CATALOGUE/search?term=$term&types=songs&limit=25&l=en-US&platform=web" +
                "&format[resources]=map",
        ) {
            header("Authorization", "Bearer $key")
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            header("User-Agent", AGENT)
            header("Accept", "application/json")
        }.bodyAsText()

        val root = LyricsProviders.json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val songs = root["resources"]?.jsonObject?.get("songs")?.jsonObject ?: return emptyList()

        return songs.values.mapNotNull { entry ->
            val attributes = (entry as? JsonObject)?.get("attributes")?.jsonObject ?: return@mapNotNull null
            val id = (entry as JsonObject)["id"]?.text() ?: return@mapNotNull null
            Hit(
                id = id,
                title = attributes["name"]?.text() ?: return@mapNotNull null,
                artist = attributes["artistName"]?.text().orEmpty(),
                seconds = attributes["durationInMillis"]?.text()?.toLongOrNull()?.let { (it / 1000).toInt() },
            )
        }
    }

    /**
     * The closest of the results, or none of them.
     *
     * Scored rather than taken in order: Apple's search is good but it will
     * happily return a cover, a live take and a remix above the studio cut.
     * Length is the strongest signal and the title is next, and anything that
     * scores badly on both is dropped — a wrong set of lyrics is worse than
     * none, because it looks like the app is broken rather than the song being
     * obscure.
     */
    private fun List<Hit>.bestFor(title: String, artist: String, seconds: Int?): Hit? =
        map { hit ->
            var score = 0
            val theirs = hit.title.lowercase()
            val ours = title.lowercase()
            if (theirs == ours) score += 50 else if (theirs.contains(ours) || ours.contains(theirs)) score += 25
            if (hit.artist.lowercase().contains(artist.lowercase()) && artist.isNotBlank()) score += 30
            if (seconds != null && hit.seconds != null) {
                val apart = kotlin.math.abs(hit.seconds - seconds)
                score += when {
                    apart <= 2 -> 40
                    apart <= 5 -> 25
                    apart <= 12 -> 8
                    else -> -20
                }
            }
            hit to score
        }.filter { it.second >= 40 }.maxByOrNull { it.second }?.first

    // ── the words ────────────────────────────────────────────────────────────

    private suspend fun words(id: String): String? {
        val body = LyricsProviders.http.get(RELAY) { parameter("id", id) }.bodyAsText()
        val root = LyricsProviders.json.parseToJsonElement(body) as? JsonObject ?: return null

        // In order of how much they carry. The TTML is the syllable-timed
        // original; the rest are the relay's own renderings of it, and each
        // one down the list has thrown something away.
        root["ttmlContent"]?.text()?.let { ttml ->
            fromTTML(ttml)?.let { return it }
        }
        root["elrcMultiPerson"]?.text()?.let { return it }
        root["elrc"]?.text()?.let { return it }
        root["plain"]?.text()?.let { return it }

        // Last resort: the structured form, flattened back into stamped lines.
        val content = root["content"]?.jsonArray ?: return null
        return content.mapNotNull { entry ->
            val line = entry as? JsonObject ?: return@mapNotNull null
            val text = line["text"]?.jsonArray
                ?.mapNotNull { (it as? JsonObject)?.get("text")?.text() }
                ?.joinToString(" ") ?: return@mapNotNull null
            val at = line["timestamp"]?.text()?.toLongOrNull() ?: return@mapNotNull null
            stamp(at / 1000.0, text)
        }.joinToString("\n").ifBlank { null }
    }

    /**
     * Apple's timed text, as stamped lines.
     *
     * Only the line timings are kept. The syllable timings inside each line are
     * what make Apple's own player highlight word by word, and this app follows
     * a line at a time — reading them and then discarding them would be work
     * for a difference nobody could see.
     */
    private fun fromTTML(ttml: String): String? {
        val lines = Regex("""<p\b[^>]*\bbegin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(ttml)
            .mapNotNull { match ->
                val at = clockOf(match.groupValues[1]) ?: return@mapNotNull null
                val text = match.groupValues[2]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&#39;", "'")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                stamp(at, text)
            }
            .toList()
        return lines.joinToString("\n").ifBlank { null }
    }

    /** `mm:ss.mmm`, `hh:mm:ss.mmm` or plain seconds, all as seconds. */
    private fun clockOf(raw: String): Double? {
        val clean = raw.removeSuffix("s")
        val parts = clean.split(":")
        return when (parts.size) {
            1 -> clean.toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h ->
                parts[1].toDoubleOrNull()?.let { m ->
                    parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it }
                }
            }
            else -> null
        }
    }

    private fun stamp(seconds: Double, text: String) = "[%02d:%02d.%02d]%s".format(
        (seconds / 60).toInt(), (seconds % 60).toInt(), ((seconds % 1) * 100).toInt(), text,
    )

    /** Everything catalogues hang off a title that Apple's index doesn't carry. */
    private fun String.plainTitle(): String {
        var out = this.trim()
        listOf(
            Regex("""\s*[\(\[][^)\]]*(official|video|audio|lyric|visualizer|hd|4k|remaster|version|edit|extended|radio|clean|explicit)[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE),
            Regex("""\s*【[^】]*】"""),
            Regex("""\s*\|.*$"""),
            Regex("""\s*[\(\[]?\s*(feat|ft)\.?\s[^)\]]*[\)\]]?""", RegexOption.IGNORE_CASE),
        ).forEach { out = out.replace(it, "") }
        return out.trim().ifEmpty { this }
    }

    /** The first artist only — Apple indexes a collaboration under one of them. */
    private fun String.firstNamed(): String {
        listOf(" & ", " and ", ", ", " x ", " feat. ", " feat ", " ft. ", " ft ", " with ").forEach {
            if (contains(it, ignoreCase = true)) return split(it, ignoreCase = true, limit = 2)[0].trim()
        }
        return trim()
    }
}
