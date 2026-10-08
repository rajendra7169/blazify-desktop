package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** A listen that has not reached the service yet, kept until it does. */
@Serializable
data class PendingListen(
    val artist: String,
    val title: String,
    val at: Long,
    val durationSeconds: Int? = null,
)

/**
 * The same record of what you played, kept by an open service instead.
 *
 * ListenBrainz asks for far less than Last.fm does: no key to register, no
 * secret, no password — one token from your own settings page, pasted once.
 * It is read back from the service straight away so a typo is caught here
 * rather than discovered a month later as a gap in the history.
 *
 * The rule for what counts as a listen is the same one: halfway or four
 * minutes, whichever comes first, and only once per play.
 *
 * A listen that can't be sent is not thrown away. It waits in a small file
 * and goes with the next one that can — a train journey's worth of songs
 * arrives when the connection does, with the times they were actually heard.
 */
object ListenBrainz {

    private const val ROOT = "https://api.listenbrainz.org/1"

    /** Where somebody finds their token. */
    const val TOKEN_PAGE = "https://listenbrainz.org/settings/"

    /** The service takes a thousand at once; a waiting list longer than that is a fortnight offline. */
    private const val BATCH = 500
    private const val QUEUE_LIMIT = 5_000
    private const val QUEUE_FILE = "listenbrainz-queue.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { HttpClient(OkHttp) { expectSuccess = false } }
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val sending = Mutex()

    private val store = File(Store.folder, "listenbrainz")

    var token by mutableStateOf("")
        private set

    /** The name the token belongs to, once the service has confirmed it. */
    var username by mutableStateOf<String?>(null)
        private set

    var recording by mutableStateOf(true)
        private set
    var announceNowPlaying by mutableStateOf(true)
        private set

    var busy by mutableStateOf(false)
        private set
    var trouble by mutableStateOf<String?>(null)
        private set

    /** How many listens are waiting for a connection. */
    var waiting by mutableStateOf(0)
        private set

    private var queue: List<PendingListen> = Store.read(QUEUE_FILE)

    val connected: Boolean get() = token.isNotBlank() && username != null

    init {
        runCatching {
            if (!store.exists()) return@runCatching
            val lines = store.readLines()
            token = lines.getOrNull(0).orEmpty()
            username = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
            recording = lines.getOrNull(2) != "false"
            announceNowPlaying = lines.getOrNull(3) != "false"
        }
        waiting = queue.size
    }

    private fun save() {
        runCatching {
            store.writeText(
                listOf(
                    token, username.orEmpty(),
                    recording.toString(), announceNowPlaying.toString(),
                ).joinToString("\n"),
            )
        }
    }

    fun chooseRecording(value: Boolean) { recording = value; save() }
    fun chooseAnnounce(value: Boolean) { announceNowPlaying = value; save() }

    /**
     * Take a pasted token and ask the service whose it is.
     *
     * Only kept once it has been accepted. A token that was mistyped and saved
     * anyway would sit there failing quietly, which is the one thing a record
     * of your listening must never do.
     */
    fun connect(pasted: String) {
        val candidate = pasted.trim()
        if (candidate.isBlank()) {
            trouble = "Paste your token first."
            return
        }
        busy = true
        trouble = null
        scope.launch {
            val answer = runCatching {
                client.get("$ROOT/validate-token") {
                    header("Authorization", "Token $candidate")
                    header("User-Agent", "Blazify/${Updates.RUNNING}")
                }.bodyAsText()
            }.getOrNull()
            busy = false
            if (answer == null) {
                trouble = "Couldn't reach ListenBrainz."
                return@launch
            }
            val root = runCatching { json.parseToJsonElement(answer) as? JsonObject }.getOrNull()
            val valid = (root?.get("valid") as? JsonPrimitive)?.content == "true"
            val name = (root?.get("user_name") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            if (!valid || name == null) {
                trouble = "That token wasn't recognised. Copy it again from your settings page."
                return@launch
            }
            token = candidate
            username = name
            save()
            flush()
        }
    }

    fun disconnect() {
        token = ""
        username = null
        trouble = null
        save()
    }

    // ── reporting ────────────────────────────────────────────────────────────

    private var startedAt = 0L
    private var reported = false

    /** A new song has started: say so, if that's wanted, and send anything still waiting. */
    fun began(track: Track) {
        startedAt = System.currentTimeMillis() / 1000
        reported = false
        if (!connected || !recording) return
        if (announceNowPlaying) {
            scope.launch {
                runCatching {
                    submit("playing_now", listOf(PendingListen(track.artistName(), track.title, 0, track.durationSeconds)))
                }
            }
        }
        // A new song is as good a moment as any to try the ones that didn't go.
        if (queue.isNotEmpty()) flush()
    }

    /** How far through the song we are. Halfway or four minutes, once. */
    fun heard(track: Track, seconds: Double) {
        if (!connected || !recording || reported) return
        val length = track.durationSeconds?.toDouble() ?: return
        if (length < 30) return
        if (seconds < minOf(length / 2, 240.0)) return
        reported = true
        enqueue(PendingListen(track.artistName(), track.title, startedAt, track.durationSeconds))
        flush()
    }

    private fun enqueue(listen: PendingListen) {
        synchronized(this) {
            queue = (queue + listen).takeLast(QUEUE_LIMIT)
            waiting = queue.size
            Store.write(QUEUE_FILE, queue)
        }
    }

    /**
     * Send what's waiting, oldest first.
     *
     * Kept on failure and tried again with the next song, quietly — a missing
     * connection is not something to interrupt the music over. Only an answer
     * that says the listens themselves are wrong drops them, because sending
     * the same bad request forever would block everything behind it.
     */
    private fun flush() {
        scope.launch {
            sending.withLock {
                while (connected) {
                    val batch = synchronized(this@ListenBrainz) { queue.take(BATCH) }
                    if (batch.isEmpty()) break
                    val status = runCatching {
                        submit(if (batch.size == 1) "single" else "import", batch).status.value
                    }.getOrNull() ?: break
                    when {
                        status in 200..299 || status == 400 -> {
                            synchronized(this@ListenBrainz) {
                                queue = queue.drop(batch.size)
                                waiting = queue.size
                                Store.write(QUEUE_FILE, queue)
                            }
                            if (status == 400) trouble = "ListenBrainz turned some listens away."
                            else if (trouble != null) trouble = null
                        }
                        status == 401 -> {
                            trouble = "ListenBrainz no longer accepts this token. Paste a new one."
                            break
                        }
                        else -> break
                    }
                }
            }
        }
    }

    private suspend fun submit(type: String, listens: List<PendingListen>): HttpResponse {
        val body = buildJsonObject {
            put("listen_type", type)
            put(
                "payload",
                buildJsonArray {
                    listens.forEach { listen ->
                        add(
                            buildJsonObject {
                                if (type != "playing_now") put("listened_at", listen.at)
                                putJsonObject("track_metadata") {
                                    put("artist_name", listen.artist)
                                    put("track_name", listen.title)
                                    putJsonObject("additional_info") {
                                        put("media_player", "Blazify")
                                        put("submission_client", "Blazify")
                                        put("submission_client_version", Updates.RUNNING)
                                        listen.durationSeconds?.let { put("duration_ms", it * 1000L) }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
        return client.post("$ROOT/submit-listens") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Token $token")
            header("User-Agent", "Blazify/${Updates.RUNNING}")
            setBody(body.toString())
        }
    }

    /** The first name only, the way the songs are filed everywhere else. */
    private fun Track.artistName(): String = artist.substringBefore(",").trim()
}
