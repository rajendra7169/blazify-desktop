package com.blazify.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URI

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Reading a playlist or album's track list off Spotify, without an account.
 *
 * Spotify's developer interface wants keys registered to a person, and the
 * key its own web player borrows is refused for everything but the player.
 * What it does hand anyone is the small player it lets other sites embed: that
 * page carries the name of the list and every song in it with its artist and
 * length, as data, because it has to draw them.
 *
 * The embed shows the first hundred songs of a playlist and no more, so a
 * longer list comes across as its first hundred and the summary says so.
 */
object Spotify {

    /** What a link points at. */
    data class Ref(val kind: String, val id: String)

    /** The most the embedded player lists. */
    const val EMBED_LIMIT = 100

    /**
     * The playlist or album a pasted link points at.
     *
     * Every form one turns up in: the web link with or without a country in
     * it and with or without the tracking tail, the app's own spotify: form,
     * and the embed link itself.
     */
    fun parse(text: String): Ref? {
        val trimmed = text.trim()
        LINK.find(trimmed)?.let { return Ref(it.groupValues[1].lowercase(), it.groupValues[2]) }
        URI_FORM.find(trimmed)?.let { return Ref(it.groupValues[1].lowercase(), it.groupValues[2]) }
        return null
    }

    private val LINK = Regex(
        "open\\.spotify\\.com/(?:intl-[a-z-]+/)?(?:embed/)?(playlist|album)/([A-Za-z0-9]{22})",
        RegexOption.IGNORE_CASE,
    )
    private val URI_FORM = Regex("spotify:(playlist|album):([A-Za-z0-9]{22})", RegexOption.IGNORE_CASE)

    /** The list's name and its songs, or why they couldn't be had. */
    suspend fun read(link: String): Result<Pair<String, List<Wanted>>> = withContext(Dispatchers.IO) {
        val ref = parse(link)
            ?: return@withContext Result.failure(Problem("That isn't a link to a Spotify playlist or album"))
        val page = fetch("https://open.spotify.com/embed/${ref.kind}/${ref.id}")
            ?: return@withContext Result.failure(Problem("Spotify didn't answer. Check the connection and try again"))
        fromEmbed(page)
    }

    /**
     * The data the embedded player draws itself from.
     *
     * Kept apart from fetching so it can be checked against a saved page.
     */
    internal fun fromEmbed(html: String): Result<Pair<String, List<Wanted>>> {
        val start = html.indexOf(NEXT_DATA)
        if (start < 0) return Result.failure(Problem("Spotify answered with a page this can't read"))
        val from = html.indexOf('>', start) + 1
        val to = html.indexOf("</script>", from)
        if (from <= 0 || to < 0) return Result.failure(Problem("Spotify answered with a page this can't read"))

        val root = runCatching { json.parseToJsonElement(html.substring(from, to)) }.getOrNull()
            ?: return Result.failure(Problem("Spotify answered with a page this can't read"))
        val props = root.at("props", "pageProps")
        if (props.at("status")?.jsonPrimitive?.intOrNull == 404) {
            return Result.failure(Problem("Spotify has no such playlist, or it is private"))
        }
        val entity = props.at("state", "data", "entity") as? JsonObject
            ?: return Result.failure(Problem("Spotify has no such playlist, or it is private"))

        val name = entity.text("name") ?: entity.text("title") ?: "Imported from Spotify"
        val songs = (entity["trackList"] as? JsonArray).orEmpty().mapNotNull { row ->
            val title = row.text("title") ?: return@mapNotNull null
            Wanted(
                title = title,
                artist = row.text("subtitle").orEmpty(),
                seconds = (row.at("duration")?.jsonPrimitive?.longOrNull)?.let { (it / 1000).toInt() },
            )
        }
        if (songs.isEmpty()) return Result.failure(Problem("That list is empty"))
        return Result.success(name to songs)
    }

    /** What the embed page is short of, when it's full. */
    fun noteFor(songs: List<Wanted>): String? =
        if (songs.size >= EMBED_LIMIT) {
            "Spotify only lists the first $EMBED_LIMIT songs of a playlist to anyone not signed in, " +
                "so that is as far as this one goes."
        } else {
            null
        }

    /** Said to the person as it is, so the message is the whole of the error. */
    class Problem(message: String) : Exception(message)

    private const val NEXT_DATA = "<script id=\"__NEXT_DATA__\""

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.at(vararg path: String): JsonElement? =
        path.fold(this) { here, key -> (here as? JsonObject)?.get(key) }

    private fun JsonElement.text(key: String): String? =
        ((this as? JsonObject)?.get(key) as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull?.trim()?.ifBlank { null }

    private fun fetch(url: String): String? = runCatching {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("User-Agent", "Blazify/1.0")
            setRequestProperty("Accept", "text/html")
        }
        connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
    }.getOrNull()
}
