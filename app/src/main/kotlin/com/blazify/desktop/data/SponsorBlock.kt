package com.blazify.desktop.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The parts of a video that aren't the song, as people who watched it marked
 * them.
 *
 * A music video often opens with a minute of scene-setting and closes with a
 * plug for the channel. Viewers of the public SponsorBlock database mark those
 * stretches, and this asks it which ones a video has.
 *
 * Asked by the first four characters of a hash of the id, never by the id
 * itself: the server answers with every video sharing that prefix and the
 * right one is picked out here, so the database never learns what is being
 * listened to.
 */
object SponsorBlock {

    private const val ROOT = "https://sponsor.ajay.app/api/skipSegments/"

    /** One kind of stretch somebody can choose to skip. */
    enum class Kind(val key: String, val label: String) {
        Talking("music_offtopic", "Talking and scenes in music videos"),
        Sponsor("sponsor", "Paid promotions"),
        SelfPromo("selfpromo", "Plugs for the channel and its merchandise"),
        Intro("intro", "Intro animations"),
        Outro("outro", "End cards and credits"),
    }

    /**
     * A stretch to jump over, in seconds from the start.
     *
     * With the length of the video it was marked on, since a video re-cut
     * after marking moves everything in it, and a jump into the wrong place
     * is worse than no jump.
     */
    data class Segment(val from: Double, val to: Double, val kind: String, val length: Double)

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 6_000
                connectTimeoutMillis = 4_000
            }
        }
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * What to skip in this video, of the kinds asked for.
     *
     * Empty on any trouble at all — no answer, a slow answer, an answer that
     * doesn't parse. The song plays either way, and a database being down is
     * not something anybody listening needs to hear about.
     */
    suspend fun segments(videoId: String, kinds: Set<Kind>): List<Segment> {
        if (kinds.isEmpty() || videoId.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val prefix = sha256(videoId).take(4)
                val response = client.get(ROOT + prefix) {
                    header("User-Agent", "Blazify")
                    parameter("categories", kinds.joinToString(",", "[", "]") { "\"${it.key}\"" })
                    parameter("actionType", "skip")
                }
                // Nothing marked is answered with a 404, which is the ordinary
                // case for most songs rather than a fault.
                if (!response.status.isSuccess()) return@runCatching emptyList()
                val videos = json.parseToJsonElement(response.bodyAsText()) as? JsonArray
                    ?: return@runCatching emptyList()
                val video = videos.firstOrNull {
                    it.jsonObject["videoID"]?.jsonPrimitive?.content == videoId
                } ?: return@runCatching emptyList()
                video.jsonObject["segments"]?.jsonArray.orEmpty().mapNotNull { entry ->
                    val fields = entry.jsonObject
                    val span = fields["segment"]?.jsonArray ?: return@mapNotNull null
                    val from = span.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val to = span.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val kind = fields["category"]?.jsonPrimitive?.content.orEmpty()
                    val length = fields["videoDuration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    // A stretch of under a second is a marker rather than
                    // something to jump over.
                    if (to - from < 1.0) null else Segment(from, to, kind, length)
                }.sortedBy { it.from }
            }.getOrDefault(emptyList())
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
