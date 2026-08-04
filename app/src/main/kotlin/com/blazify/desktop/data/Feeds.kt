package com.blazify.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Podcasts as the rest of the world publishes them.
 *
 * A podcast is not really a thing on a music service — it is a file on the
 * maker's own server and a feed that lists them. The directory used here is
 * the public one every podcast application on earth reads: it needs no key, no
 * account and no permission, and it knows about shows the music catalogue has
 * never heard of.
 *
 * What it buys is not only more programmes. An episode from a feed is an
 * ordinary file that can be asked for by the byte, so seeking is instant, a
 * reconnection is just another request, and keeping a copy is a download
 * rather than a negotiation. None of the things that make a long recording
 * difficult on a streaming service apply here at all.
 *
 * What it does not have is the local long tail: programmes whose makers never
 * registered a feed, and publish where their audience already is. Measured, in
 * both directions — which is why this sits alongside the catalogue rather than
 * replacing it.
 */
object Feeds {

    private const val DIRECTORY = "https://itunes.apple.com"
    private const val CHARTS = "https://rss.marketingtools.apple.com/api/v2"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Which country's charts to show.
     *
     * Taken from the machine to begin with, because being asked to pick a
     * country before a page will load is a form standing in front of a chart.
     * But the machine is often wrong about this — a desktop set to English
     * says United States wherever it happens to be sitting — so the answer can
     * be corrected, and the correction is remembered.
     */
    var country: String = runCatching { kept.readText().trim().ifBlank { null } }.getOrNull()
        ?: java.util.Locale.getDefault().country.lowercase().ifBlank { "us" }
        private set

    private val kept: java.io.File get() = java.io.File(Store.folder, "podcast-country")

    fun chartFrom(code: String) {
        country = code.lowercase()
        runCatching { kept.writeText(country) }
    }

    /**
     * The places worth offering.
     *
     * Not a list of every country on earth: a chart is a thing somebody looks
     * at for their own place or a place they follow, and two hundred entries
     * turns a glance into a search. Whatever the machine says goes on the front
     * whether or not it is here already.
     */
    val places: List<Pair<String, String>>
        get() {
            val common = listOf(
                "np" to "Nepal", "in" to "India", "us" to "United States",
                "gb" to "United Kingdom", "au" to "Australia", "ca" to "Canada",
                "ae" to "UAE", "sg" to "Singapore", "de" to "Germany", "jp" to "Japan",
            )
            val mine = java.util.Locale.getDefault().country.lowercase()
            return if (mine.isBlank() || common.any { it.first == mine }) common
            else listOf(mine to java.util.Locale.of("", mine).displayCountry) + common
        }

    /** One programme in the directory. */
    data class Show(
        val id: String,
        val title: String,
        val author: String,
        val artwork: String?,
        val feed: String,
        val episodeCount: Int?,
        val genre: String?,
    ) {
        /** As a tile, so a page can hold these and catalogue ones side by side. */
        fun asCard() = Catalogue.Card(
            id = "feed:$feed",
            title = title,
            subtitle = author,
            thumbnail = artwork,
            kind = Catalogue.Kind.Playlist,
        )
    }

    /**
     * Look something up by name.
     *
     * The directory answers in a hundred milliseconds and has no idea who is
     * asking, which is the whole appeal.
     */
    suspend fun search(words: String, limit: Int = 24): List<Show> = withContext(Dispatchers.IO) {
        val asked = URLEncoder.encode(words, "UTF-8")
        val body = fetch("$DIRECTORY/search?media=podcast&limit=$limit&country=$country&term=$asked")
            ?: return@withContext emptyList()
        runCatching {
            json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty().mapNotNull { it.asShow() }
        }.getOrDefault(emptyList())
    }

    /**
     * What a country is actually listening to.
     *
     * The one thing the music catalogue cannot offer for programmes at all:
     * an editorial chart, by place, without an account.
     */
    suspend fun chart(where: String = country, limit: Int = 20): List<Show> = withContext(Dispatchers.IO) {
        val body = fetch("$CHARTS/$where/podcasts/top/$limit/podcasts.json")
            // Not every country has one. Falling back is better than a rail
            // that is empty for the person it was meant for.
            ?: fetch("$CHARTS/us/podcasts/top/$limit/podcasts.json")
            ?: return@withContext emptyList()

        val named = runCatching {
            json.parseToJsonElement(body).jsonObject["feed"]?.jsonObject
                ?.get("results")?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        }.getOrDefault(emptyList())

        // The chart gives names and artwork but no feed, and a show without a
        // feed has no episodes. Each is looked up once by name; they are
        // fetched together rather than in turn, or a chart of twenty would
        // take twenty times as long as it needs to.
        kotlinx.coroutines.coroutineScope {
            named.map { name -> async { search(name, limit = 1).firstOrNull() } }
                .mapNotNull { it.await() }
        }
    }

    /** One episode of a programme. */
    data class Episode(
        val title: String,
        val show: String,
        val audio: String,
        val artwork: String?,
        val seconds: Int?,
        val published: String?,
    ) {
        /**
         * As something playable.
         *
         * The link goes with it, so nothing has to be asked of anybody when
         * this reaches the front of a queue.
         */
        fun asTrack() = Track(
            id = "feed:${audio.substringBefore('?').takeLast(80)}",
            title = title,
            artist = show,
            thumbnail = artwork,
            durationSeconds = seconds,
            stream = audio,
            from = Origin.Feed,
        )
    }

    /**
     * The episodes of a programme, from its own feed.
     *
     * Read with the parser that ships with the runtime — a podcast feed is
     * ordinary XML and pulling in a library to read four fields out of it
     * would be a dependency for the sake of one.
     */
    suspend fun episodes(feed: String, limit: Int = 60): List<Episode> = withContext(Dispatchers.IO) {
        val body = fetchBytes(feed) ?: return@withContext emptyList()
        runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                // A feed is somebody else's document. It has no business
                // naming files on this machine, and this is the switch that
                // says so.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(body))
            val channel = document.getElementsByTagName("channel").item(0) as? Element
            val showName = channel?.text("title").orEmpty()
            val showArt = channel?.let { image(it) }

            val items = document.getElementsByTagName("item")
            (0 until minOf(items.length, limit)).mapNotNull { at ->
                val item = items.item(at) as? Element ?: return@mapNotNull null
                val enclosure = item.getElementsByTagName("enclosure").item(0) as? Element
                val audio = enclosure?.getAttribute("url").orEmpty()
                if (audio.isBlank()) return@mapNotNull null
                Episode(
                    title = item.text("title") ?: "Untitled",
                    show = showName,
                    audio = audio,
                    artwork = image(item) ?: showArt,
                    seconds = item.text("itunes:duration")?.let(::seconds),
                    published = item.text("pubDate")?.take(16),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Whether a tile stands for a programme from a feed rather than the catalogue. */
    fun isFeed(id: String) = id.startsWith("feed:")

    /** The feed a tile stands for. */
    fun feedOf(id: String) = id.removePrefix("feed:")

    // ── the plumbing ────────────────────────────────────────────────────────

    private fun kotlinx.serialization.json.JsonElement.asShow(): Show? = runCatching {
        val row = jsonObject
        val feed = row["feedUrl"]?.jsonPrimitive?.content ?: return null
        Show(
            id = row["collectionId"]?.jsonPrimitive?.content.orEmpty(),
            title = row["collectionName"]?.jsonPrimitive?.content ?: return null,
            author = row["artistName"]?.jsonPrimitive?.content.orEmpty(),
            artwork = row["artworkUrl600"]?.jsonPrimitive?.content
                ?: row["artworkUrl100"]?.jsonPrimitive?.content,
            feed = feed,
            episodeCount = row["trackCount"]?.jsonPrimitive?.content?.toIntOrNull(),
            genre = row["primaryGenreName"]?.jsonPrimitive?.content,
        )
    }.getOrNull()

    /** A duration as feeds write it: seconds, or minutes and seconds, or all three. */
    private fun seconds(written: String): Int? {
        val parts = written.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    /** Artwork, which feeds attach in three different ways and never the same one twice. */
    private fun image(element: Element): String? {
        val itunes = element.getElementsByTagName("itunes:image")
        for (at in 0 until itunes.length) {
            (itunes.item(at) as? Element)?.getAttribute("href")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val plain = element.getElementsByTagName("url")
        for (at in 0 until plain.length) {
            plain.item(at)?.textContent?.trim()?.takeIf { it.startsWith("http") }?.let { return it }
        }
        return null
    }

    /** A child's text, by name, without descending into the whole document. */
    private fun Element.text(name: String): String? {
        var child: Node? = firstChild
        while (child != null) {
            if (child.nodeName == name) return child.textContent?.trim()?.ifBlank { null }
            child = child.nextSibling
        }
        return null
    }

    private fun fetch(url: String): String? = fetchBytes(url)?.toString(Charsets.UTF_8)

    private fun fetchBytes(url: String): ByteArray? = runCatching {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("User-Agent", AGENT)
            setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml")
        }
        connection.inputStream.use { it.readBytes() }
    }.getOrNull()

    /**
     * Named honestly.
     *
     * Feeds are read by hundreds of applications and their makers look at who
     * asked. Pretending to be a browser to read a public feed would be a lie
     * told for no reason.
     */
    private const val AGENT = "Blazify/1.0 (+podcast client)"
}
