package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.security.MessageDigest

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Keeping a record of what you listened to, somewhere that isn't here.
 *
 * A play is only worth reporting once it has actually been a play, so a song is
 * sent when it passes halfway or four minutes, whichever comes first — the rule
 * the service itself asks for, and the reason skipping through an album doesn't
 * fill your history with songs you heard eight seconds of.
 *
 * Credentials are the listener's own. There is no key baked into this build:
 * one shipped in an open repository is a key anybody can lift, and a key that
 * gets abused is revoked for everybody using it. Making your own takes a minute
 * and it stays yours.
 */
object Scrobbler {

    private const val ROOT = "https://ws.audioscrobbler.com/2.0/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { HttpClient(OkHttp) { expectSuccess = false } }
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val store = File(Store.folder, "lastfm")

    var apiKey by mutableStateOf("")
        private set
    var secret by mutableStateOf("")
        private set
    var session by mutableStateOf<String?>(null)
        private set
    var username by mutableStateOf<String?>(null)
        private set

    /** Whether plays are reported at all. Off until there is somewhere to report to. */
    var scrobbling by mutableStateOf(true)
        private set

    /** Whether the service is told what's playing before it finishes. */
    var announceNowPlaying by mutableStateOf(true)
        private set

    /** Whether liking a song here also loves it there. */
    var mirrorLikes by mutableStateOf(true)
        private set

    var busy by mutableStateOf(false)
        private set
    var trouble by mutableStateOf<String?>(null)
        private set

    val signedIn: Boolean get() = !session.isNullOrBlank()
    val configured: Boolean get() = apiKey.isNotBlank() && secret.isNotBlank()

    init {
        runCatching {
            if (!store.exists()) return@runCatching
            val lines = store.readLines()
            apiKey = lines.getOrNull(0).orEmpty()
            secret = lines.getOrNull(1).orEmpty()
            session = lines.getOrNull(2)?.takeIf { it.isNotBlank() }
            username = lines.getOrNull(3)?.takeIf { it.isNotBlank() }
            scrobbling = lines.getOrNull(4) != "false"
            announceNowPlaying = lines.getOrNull(5) != "false"
            mirrorLikes = lines.getOrNull(6) != "false"
        }
    }

    private fun save() {
        runCatching {
            store.writeText(
                listOf(
                    apiKey, secret, session.orEmpty(), username.orEmpty(),
                    scrobbling.toString(), announceNowPlaying.toString(), mirrorLikes.toString(),
                ).joinToString("\n"),
            )
        }
    }

    fun chooseApiKey(value: String) { apiKey = value.trim(); save() }
    fun chooseSecret(value: String) { secret = value.trim(); save() }
    fun chooseScrobbling(value: Boolean) { scrobbling = value; save() }
    fun chooseAnnounce(value: Boolean) { announceNowPlaying = value; save() }
    fun chooseMirrorLikes(value: Boolean) { mirrorLikes = value; save() }

    // ── signing in ───────────────────────────────────────────────────────────

    /**
     * Trade a name and password for a session key.
     *
     * The password is sent once, over TLS, and never written down — what is kept
     * is the session key the service hands back, which can be revoked from your
     * account page without changing anything else.
     */
    fun signIn(name: String, password: String) {
        if (!configured) {
            trouble = "Add an API key and secret first."
            return
        }
        busy = true
        trouble = null
        scope.launch {
            val answer = call(
                "auth.getMobileSession",
                mapOf("username" to name, "password" to password),
                signed = true,
            )
            busy = false
            val body = answer.getOrNull()
            if (body == null) {
                trouble = "Couldn't reach the service."
                return@launch
            }
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            val key = root?.get("session")?.jsonObject?.get("key")?.text()
            if (key == null) {
                trouble = root?.get("message")?.text() ?: "That sign-in was refused."
                return@launch
            }
            session = key
            username = root["session"]?.jsonObject?.get("name")?.text() ?: name
            save()
        }
    }

    fun signOut() {
        session = null
        username = null
        trouble = null
        save()
    }

    // ── reporting ────────────────────────────────────────────────────────────

    private var startedAt = 0L
    private var reported = false
    private var listened = 0.0

    /**
     * A new song has started.
     *
     * The one before it is settled first — a skip after four minutes still
     * counted as a play, and forgetting to send it because something else began
     * would lose it.
     */
    fun began(track: Track) {
        finish()
        startedAt = System.currentTimeMillis() / 1000
        reported = false
        listened = 0.0
        if (signedIn && announceNowPlaying && scrobbling) {
            scope.launch {
                call(
                    "track.updateNowPlaying",
                    buildMap {
                        put("artist", track.artist.substringBefore(",").trim())
                        put("track", track.title)
                        track.durationSeconds?.let { put("duration", it.toString()) }
                    },
                    signed = true,
                    withSession = true,
                )
            }
        }
    }

    /**
     * How far through the song we are, called as it plays.
     *
     * Halfway or four minutes, whichever is sooner — and only once. Anything
     * shorter than half a minute is never reported, because the service will
     * refuse it and because it wasn't a play.
     */
    fun heard(track: Track, seconds: Double) {
        if (!signedIn || !scrobbling || reported) return
        listened = seconds
        val length = track.durationSeconds?.toDouble() ?: return
        if (length < 30) return
        if (seconds < minOf(length / 2, 240.0)) return
        reported = true
        send(track)
    }

    /** Called when a song is left, in case it earned its place on the way out. */
    fun finish() {
        reported = true
    }

    private fun send(track: Track) {
        val at = startedAt
        scope.launch {
            call(
                "track.scrobble",
                buildMap {
                    put("artist[0]", track.artist.substringBefore(",").trim())
                    put("track[0]", track.title)
                    put("timestamp[0]", at.toString())
                    track.durationSeconds?.let { put("duration[0]", it.toString()) }
                },
                signed = true,
                withSession = true,
            )
        }
    }

    /** Liking something here can mean loving it there, if that's wanted. */
    fun love(track: Track, loved: Boolean) {
        if (!signedIn || !mirrorLikes) return
        scope.launch {
            call(
                if (loved) "track.love" else "track.unlove",
                mapOf(
                    "artist" to track.artist.substringBefore(",").trim(),
                    "track" to track.title,
                ),
                signed = true,
                withSession = true,
            )
        }
    }

    // ── the wire ─────────────────────────────────────────────────────────────

    /**
     * Every call is signed.
     *
     * The signature is an MD5 of every parameter in key order with the secret on
     * the end — the service's own scheme, and the reason the secret never
     * travels: it is proved rather than sent.
     */
    private suspend fun call(
        method: String,
        extra: Map<String, String>,
        signed: Boolean,
        withSession: Boolean = false,
    ): Result<String> = runCatching {
        val params = buildMap {
            put("method", method)
            put("api_key", apiKey)
            if (withSession) session?.let { put("sk", it) }
            putAll(extra)
        }
        val body = Parameters.build {
            params.forEach { (key, value) -> append(key, value) }
            if (signed) append("api_sig", params.signature())
            append("format", "json")
        }
        client.post(ROOT) {
            header("User-Agent", "Blazify")
            setBody(FormDataContent(body))
        }.bodyAsText()
    }

    private fun Map<String, String>.signature(): String {
        val flat = toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5")
            .digest(flat.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun kotlinx.serialization.json.JsonElement.text(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
