package com.blazify.desktop.data

import com.blazify.innertube.YouTube
import com.blazify.innertube.models.WatchEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The places words can come from.
 *
 * No one service has everything. LrcLib is excellent for Western pop and thin
 * on everything else; KuGou is the reverse; the service YouTube Music itself
 * uses knows the catalogue but rarely has timings. Asking one and giving up is
 * why lyrics "don't work" for whole genres, so the app asks several in an order
 * you can set.
 *
 * Each returns a raw LRC or plain transcript. Nothing here parses or ranks —
 * that belongs to whoever asked, and keeping it out means a new source is one
 * object and no other changes.
 */
sealed interface LyricsProvider {
    val name: String

    /** The words, or null if this source hasn't got them. */
    suspend fun find(track: Track): String?
}

/** Everything the app knows how to ask, in the order it asks by default. */
object LyricsProviders {

    // Captions last of all, and only ever answering for talk. They are the
    // right answer where nobody wrote the words down and the wrong one
    // everywhere else, so they wait until every source that deals in written
    // lyrics has said no.
    val all: List<LyricsProvider> = listOf(Paxsenix, LrcLib, LyricsPlus, KuGou, YouTubeMusic, Captions)

    fun byName(name: String): LyricsProvider? = all.firstOrNull { it.name == name }

    internal val http by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                // A source that isn't answering must not hold up the ones after
                // it: the whole point of a list is that a slow link is skipped,
                // not waited on.
                requestTimeoutMillis = 12_000
                connectTimeoutMillis = 8_000
            }
            expectSuccess = false
        }
    }

    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }
}

/**
 * A community lyrics database, matched on title, artist and length.
 *
 * Length is what separates a studio cut from a live take of the same song, so
 * it's sent when we know it. The exact lookup is precise and unforgiving — a
 * couple of seconds out and there is simply no answer — so a search follows it,
 * which costs one request and catches everything timed slightly differently.
 */
object LrcLib : LyricsProvider {
    override val name = "LrcLib"

    override suspend fun find(track: Track): String? {
        val title = track.title.cleanedTitle()
        val artist = track.artist.substringBefore(",").trim()
        return exact(title, artist, track.durationSeconds) ?: search(title, artist, track.durationSeconds)
    }

    private suspend fun exact(title: String, artist: String, seconds: Int?): String? = runCatching {
        val body = LyricsProviders.http.get("https://lrclib.net/api/get") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            seconds?.let { parameter("duration", it) }
        }.bodyAsText()
        LyricsProviders.json.parseToJsonElement(body).jsonObject.words()
    }.getOrNull()

    /**
     * The nearest match by name, preferring one that runs about as long.
     *
     * A search turns up remasters, live cuts and covers under one title. Length
     * tells them apart, so results within a few seconds of ours come first, and
     * a timed transcript beats a flat one at the same distance.
     */
    private suspend fun search(title: String, artist: String, seconds: Int?): String? = runCatching {
        val body = LyricsProviders.http.get("https://lrclib.net/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
        }.bodyAsText()

        val results = LyricsProviders.json.parseToJsonElement(body) as? JsonArray ?: return null
        results.mapNotNull { it.jsonObject }
            .minByOrNull { entry ->
                val length = entry["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
                val apart = if (seconds != null && length != null) kotlin.math.abs(length - seconds) else 30.0
                val timed = entry["syncedLyrics"]?.text() != null
                apart + if (timed) 0.0 else 5.0
            }
            ?.words()
    }.getOrNull()

    private fun JsonObject.words(): String? = this["syncedLyrics"]?.text() ?: this["plainLyrics"]?.text()
}

/**
 * A community project serving word-by-word transcripts.
 *
 * Several people mirror it and any one of them can be down, so the list is
 * tried in turn and the one that answered is remembered — a session shouldn't
 * pay for a dead mirror more than once.
 */
object LyricsPlus : LyricsProvider {
    override val name = "LyricsPlus"

    private val mirrors = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus-seven.vercel.app",
    )

    @Volatile
    private var lastGood: String? = null

    override suspend fun find(track: Track): String? {
        val ordered = lastGood?.let { good -> listOf(good) + mirrors.filterNot { it == good } } ?: mirrors
        for (host in ordered) {
            val found = runCatching { ask(host, track) }.getOrNull()
            if (found != null) {
                lastGood = host
                return found
            }
        }
        return null
    }

    private suspend fun ask(host: String, track: Track): String? {
        val body = LyricsProviders.http.get("$host/v2/lyrics/get") {
            parameter("title", track.title.cleanedTitle())
            parameter("artist", track.artist.substringBefore(",").trim())
            track.durationSeconds?.takeIf { it > 0 }?.let { parameter("duration", it) }
        }.bodyAsText()

        val root = LyricsProviders.json.parseToJsonElement(body) as? JsonObject ?: return null
        val lines = root["lyrics"] as? JsonArray ?: return null
        if (lines.isEmpty()) return null

        // Rendered back into LRC rather than kept in its own shape, so every
        // source downstream of here looks the same and the parser stays one
        // parser.
        return lines.mapNotNull { entry ->
            val line = entry as? JsonObject ?: return@mapNotNull null
            val text = line["text"]?.text() ?: return@mapNotNull null
            val start = line["time"]?.jsonPrimitive?.longOrNull
                ?: line["startTime"]?.jsonPrimitive?.longOrNull
                ?: return@mapNotNull null
            // Milliseconds on the wire.
            val seconds = start / 1000.0
            "[%02d:%02d.%02d]%s".format(
                (seconds / 60).toInt(), (seconds % 60).toInt(),
                ((seconds % 1) * 100).toInt(), text,
            )
        }.joinToString("\n").ifBlank { null }
    }
}

/**
 * A Chinese music service, and by some way the best source for Mandarin,
 * Cantonese, Japanese and Korean tracks — the ones the Western databases are
 * thinnest on.
 *
 * Its search wants the title and artist run together the way its own app sends
 * them, and answers with a candidate list; the words themselves are a second
 * request keyed on an id and a one-shot access key.
 */
object KuGou : LyricsProvider {
    override val name = "KuGou"

    override suspend fun find(track: Track): String? = runCatching {
        val keyword = "${track.title.normalisedTitle()} - ${track.artist.normalisedArtist()}"
        val body = LyricsProviders.http.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            track.durationSeconds?.takeIf { it > 0 }?.let { parameter("duration", it * 1000) }
            url.encodedParameters.append("keyword", keyword.encodeURLParameter(spaceToPlus = false))
        }.bodyAsText()

        val candidates = (LyricsProviders.json.parseToJsonElement(body) as? JsonObject)
            ?.get("candidates")?.jsonArray ?: return null

        for (entry in candidates.take(3)) {
            val candidate = entry as? JsonObject ?: continue
            val id = candidate["id"]?.text() ?: continue
            val key = candidate["accesskey"]?.text() ?: continue
            val words = download(id, key)
            if (!words.isNullOrBlank()) return words
        }
        null
    }.getOrNull()

    private suspend fun download(id: String, accessKey: String): String? = runCatching {
        val body = LyricsProviders.http.get("https://lyrics.kugou.com/download") {
            parameter("fmt", "lrc")
            parameter("charset", "utf8")
            parameter("client", "pc")
            parameter("ver", 1)
            parameter("id", id)
            parameter("accesskey", accessKey)
        }.bodyAsText()

        val encoded = (LyricsProviders.json.parseToJsonElement(body) as? JsonObject)
            ?.get("content")?.text() ?: return null
        String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }.getOrNull()

    /** Its index doesn't carry the bracketed decoration ours does. */
    private fun String.normalisedTitle() = replace(Regex("[(（<《〈＜「『][^)）>》〉＞」』]*[)）>》〉＞」』]"), "").trim()

    private fun String.normalisedArtist() =
        replace(", ", "、").replace(" & ", "、").replace(".", "").trim()
}

/**
 * The words YouTube Music shows for the song itself.
 *
 * Almost never timed, so it sits last by default — but it is the one source
 * that is looking at exactly the recording being played rather than guessing
 * from a title, which makes it the right answer when everything else has
 * matched the wrong take or found nothing at all.
 */
object YouTubeMusic : LyricsProvider {
    override val name = "YouTube Music"

    override suspend fun find(track: Track): String? = runCatching {
        val next = YouTube.next(WatchEndpoint(videoId = track.id)).getOrNull() ?: return null
        val endpoint = next.lyricsEndpoint ?: return null
        YouTube.lyrics(endpoint).getOrNull()?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * Strip the decoration catalogues hang off titles.
 *
 * "Kesariya (From "Brahmastra")" won't match anything; "Kesariya" will.
 */
internal fun String.cleanedTitle(): String =
    replace(Regex("\\s*[\\(\\[][^)\\]]*[\\)\\]]"), "").trim().ifEmpty { this }

/** A JSON value as text, treating blanks and an explicit null as absent. */
internal fun kotlinx.serialization.json.JsonElement.text(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.let {
        it.contentOrNull ?: it.intOrNull?.toString() ?: it.longOrNull?.toString()
    }?.takeIf { it.isNotBlank() && it != "null" }
