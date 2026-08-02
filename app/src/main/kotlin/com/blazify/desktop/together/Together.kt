package com.blazify.desktop.together

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Store
import com.blazify.desktop.data.Track
import com.blazify.desktop.together.proto.Wire
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Listening to the same thing at the same time, from different rooms.
 *
 * One person hosts and everybody else follows: the host's transport is the
 * room's transport, and a guest pressing play would only mean two songs at
 * once. Guests can suggest, and the host decides — which is the arrangement
 * that stops a shared queue from turning into a fight over the aux cable.
 *
 * Nothing is streamed between machines. Everyone plays the same song from their
 * own copy of the catalogue and the room agrees on *which* song and *where in
 * it* — so the audio is as good as each person's connection rather than as good
 * as the worst one, and the server only ever carries a few hundred bytes.
 *
 * The server is the one the phone uses, deliberately. A room is only worth
 * having if the people you want in it can reach it from whatever they happen to
 * be holding.
 */
object Together {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { HttpClient(OkHttp) { install(WebSockets) } }

    private var socket: io.ktor.websocket.WebSocketSession? = null
    private var pump: Job? = null
    private var heartbeat: Job? = null

    enum class Link { Off, Dialling, On }

    var link by mutableStateOf(Link.Off)
        private set

    /** The room's four letters, once there is a room. */
    var code by mutableStateOf<String?>(null)
        private set

    var hosting by mutableStateOf(false)
        private set

    var me by mutableStateOf<String?>(null)
        private set

    /** Anything the server refused, in words worth showing. */
    var trouble by mutableStateOf<String?>(null)
        private set

    val listeners = mutableStateListOf<Listener>()
    val knocking = mutableStateListOf<Knock>()
    val suggestions = mutableStateListOf<Suggestion>()

    data class Listener(val id: String, val name: String, val host: Boolean, val here: Boolean)

    /** Somebody at the door, waiting to be let in. */
    data class Knock(val id: String, val name: String)

    data class Suggestion(val id: String, val from: String, val track: Track)

    /**
     * The name others see.
     *
     * The account's if there is one, since that is the name the same person
     * already goes by on the phone; otherwise the machine's, which is at least
     * something they will recognise.
     */
    val myName: String
        get() = Account.name ?: System.getProperty("user.name") ?: "Blazify"

    private val ticket = File(Store.folder, "together-session")

    // ── the line ─────────────────────────────────────────────────────────────

    private fun dial(then: suspend () -> Unit) {
        if (link == Link.On) {
            scope.launch { then() }
            return
        }
        if (link == Link.Dialling) return

        link = Link.Dialling
        trouble = null
        pump = scope.launch {
            try {
                val session = client.webSocketSession(Servers.DEFAULT)
                socket = session
                link = Link.On
                startHeartbeat()
                then()
                session.incoming.consumeEach { frame ->
                    if (frame is Frame.Binary) heard(frame.readBytes())
                }
            } catch (_: Exception) {
                trouble = "Couldn't reach the room server. Check the connection and try again."
            } finally {
                hangUp(keepTicket = true)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            // Quiet enough not to matter, often enough that a dropped line is
            // noticed in seconds rather than whenever somebody presses
            // something.
            while (isActive) {
                delay(15_000)
                say(Say.PING, Wire.PingPayload.newBuilder().setClientTime(now()).build())
            }
        }
    }

    private fun hangUp(keepTicket: Boolean) {
        heartbeat?.cancel()
        heartbeat = null
        socket = null
        link = Link.Off
        if (!keepTicket) {
            code = null
            hosting = false
            me = null
            listeners.clear()
            knocking.clear()
            suggestions.clear()
            runCatching { ticket.delete() }
        }
    }

    // ── what we say ──────────────────────────────────────────────────────────

    private fun say(type: String, payload: MessageLite?) {
        val session = socket ?: return
        val envelope = Wire.Envelope.newBuilder()
            .setType(type)
            .setPayload(payload?.let { ByteString.copyFrom(it.toByteArray()) } ?: ByteString.EMPTY)
            .setCompressed(false)
            .build()
        scope.launch { runCatching { session.send(Frame.Binary(true, envelope.toByteArray())) } }
    }

    fun host() = dial {
        say(Say.CREATE_ROOM, Wire.CreateRoomPayload.newBuilder().setUsername(myName).build())
    }

    fun join(room: String) = dial {
        say(
            Say.JOIN_ROOM,
            Wire.JoinRoomPayload.newBuilder()
                .setRoomCode(room.trim().uppercase())
                .setUsername(myName)
                .build(),
        )
    }

    fun leave() {
        say(Say.LEAVE_ROOM, null)
        scope.launch { runCatching { socket?.close() } }
        hangUp(keepTicket = false)
    }

    fun letIn(id: String) {
        say(Say.APPROVE_JOIN, Wire.ApproveJoinPayload.newBuilder().setUserId(id).build())
        knocking.removeAll { it.id == id }
    }

    fun turnAway(id: String) {
        say(Say.REJECT_JOIN, Wire.RejectJoinPayload.newBuilder().setUserId(id).build())
        knocking.removeAll { it.id == id }
    }

    fun remove(id: String) =
        say(Say.KICK_USER, Wire.KickUserPayload.newBuilder().setUserId(id).build())

    fun handOver(id: String) =
        say(Say.TRANSFER_HOST, Wire.TransferHostPayload.newBuilder().setNewHostId(id).build())

    /** A guest putting something forward. The host decides. */
    fun suggest(track: Track) =
        say(Say.SUGGEST_TRACK, Wire.SuggestTrackPayload.newBuilder().setTrackInfo(track.wire()).build())

    fun accept(suggestion: Suggestion) {
        say(
            Say.APPROVE_SUGGESTION,
            Wire.ApproveSuggestionPayload.newBuilder().setSuggestionId(suggestion.id).build(),
        )
        PlayerState.addToQueue(suggestion.track)
        suggestions.removeAll { it.id == suggestion.id }
        share()
    }

    fun decline(suggestion: Suggestion) {
        say(
            Say.REJECT_SUGGESTION,
            Wire.RejectSuggestionPayload.newBuilder().setSuggestionId(suggestion.id).build(),
        )
        suggestions.removeAll { it.id == suggestion.id }
    }

    /**
     * Tell the room where the host is up to.
     *
     * Called on every transport change rather than on a timer: a room only
     * needs to hear from the host when something has actually happened, and a
     * message per second would be a message per second of silence too.
     */
    fun share(action: String = Did.SYNC_QUEUE) {
        if (!hosting || link != Link.On) return
        val playing = PlayerState.current ?: return
        say(
            Say.PLAYBACK_ACTION,
            Wire.PlaybackActionPayload.newBuilder()
                .setAction(action)
                .setTrackId(playing.id)
                .setPosition((PlayerState.positionSeconds * 1000).toLong())
                .setTrackInfo(playing.wire())
                .addAllQueue(PlayerState.queue.map { it.wire() })
                .setQueueTitle(PlayerState.playingFrom ?: "")
                .setServerTime(now())
                .build(),
        )
    }

    // ── what we hear ─────────────────────────────────────────────────────────

    private fun heard(bytes: ByteArray) {
        val envelope = runCatching { Wire.Envelope.parseFrom(bytes) }.getOrNull() ?: return
        val body = envelope.payload.toByteArray().let {
            if (envelope.compressed) unzip(it) else it
        }

        when (envelope.type) {
            Say.ROOM_CREATED -> Wire.RoomCreatedPayload.parseFrom(body).let {
                code = it.roomCode
                me = it.userId
                hosting = true
                runCatching { ticket.writeText("${it.roomCode}\n${it.sessionToken}") }
                listeners.clear()
                listeners += Listener(it.userId, myName, host = true, here = true)
            }

            Say.JOIN_APPROVED -> Wire.JoinApprovedPayload.parseFrom(body).let {
                code = it.roomCode
                me = it.userId
                hosting = false
                runCatching { ticket.writeText("${it.roomCode}\n${it.sessionToken}") }
                take(it.state)
            }

            Say.JOIN_REJECTED -> Wire.JoinRejectedPayload.parseFrom(body).let {
                trouble = it.reason.ifBlank { "The host didn't let you in." }
                hangUp(keepTicket = false)
            }

            // Only the host is ever asked, and only the host can answer.
            Say.JOIN_REQUEST -> Wire.JoinRequestPayload.parseFrom(body).let {
                knocking += Knock(it.userId, it.username)
            }

            Say.USER_JOINED -> Wire.UserJoinedPayload.parseFrom(body).let {
                listeners += Listener(it.userId, it.username, host = false, here = true)
            }

            Say.USER_LEFT -> Wire.UserLeftPayload.parseFrom(body).let { gone ->
                listeners.removeAll { it.id == gone.userId }
            }

            Say.USER_DISCONNECTED -> Wire.UserDisconnectedPayload.parseFrom(body).let { off ->
                mark(off.userId, here = false)
            }

            Say.USER_RECONNECTED -> Wire.UserReconnectedPayload.parseFrom(body).let { back ->
                mark(back.userId, here = true)
            }

            Say.HOST_CHANGED -> Wire.HostChangedPayload.parseFrom(body).let { change ->
                hosting = change.newHostId == me
                for (at in listeners.indices) {
                    listeners[at] = listeners[at].copy(host = listeners[at].id == change.newHostId)
                }
            }

            Say.KICKED -> Wire.KickedPayload.parseFrom(body).let {
                trouble = it.reason.ifBlank { "The host removed you from the room." }
                hangUp(keepTicket = false)
            }

            Say.SYNC_PLAYBACK, Say.SYNC_STATE -> follow(Wire.SyncStatePayload.parseFrom(body))

            Say.SUGGESTION_RECEIVED -> Wire.SuggestionReceivedPayload.parseFrom(body).let {
                suggestions += Suggestion(it.suggestionId, it.fromUsername, it.trackInfo.ours())
            }

            Say.ERROR -> Wire.ErrorPayload.parseFrom(body).let {
                trouble = it.message.ifBlank { "The room server refused that." }
            }

            else -> Unit
        }
    }

    private fun mark(id: String, here: Boolean) {
        val at = listeners.indexOfFirst { it.id == id }
        if (at >= 0) listeners[at] = listeners[at].copy(here = here)
    }

    private fun take(state: Wire.RoomState) {
        listeners.clear()
        state.usersList.forEach {
            listeners += Listener(it.userId, it.username, it.isHost, it.isConnected)
        }
        follow(
            Wire.SyncStatePayload.newBuilder()
                .setCurrentTrack(state.currentTrack)
                .setIsPlaying(state.isPlaying)
                .setPosition(state.position)
                .addAllQueue(state.queueList)
                .build(),
        )
    }

    /**
     * Do what the host is doing.
     *
     * The position is only forced when it is meaningfully out — chasing every
     * update to the millisecond would mean a seek on every message, and a
     * player that seeks constantly stutters. Half a second apart is closer than
     * two people in one room with two phones ever manage.
     */
    private fun follow(state: Wire.SyncStatePayload) {
        if (hosting) return

        val queue = state.queueList.map { it.ours() }
        val wanted = state.currentTrack.ours()
        if (wanted.id.isBlank()) return

        val at = queue.indexOfFirst { it.id == wanted.id }
        val changed = PlayerState.current?.id != wanted.id

        if (changed) {
            if (queue.isNotEmpty() && at >= 0) PlayerState.play(queue, at, "Blaze Together")
            else PlayerState.play(listOf(wanted), 0, "Blaze Together")
        }

        val theirs = state.position / 1000.0
        if (kotlin.math.abs(theirs - PlayerState.positionSeconds) > 1.2) {
            PlayerState.seekTo(theirs)
        }
        if (state.isPlaying != PlayerState.playing) PlayerState.toggle()
    }

    // ── odds and ends ────────────────────────────────────────────────────────

    private fun now() = System.currentTimeMillis()

    private fun unzip(data: ByteArray): ByteArray =
        runCatching { GZIPInputStream(data.inputStream()).use { it.readBytes() } }.getOrDefault(data)

    private fun Track.wire(): Wire.TrackInfo = Wire.TrackInfo.newBuilder()
        .setId(id)
        .setTitle(title)
        .setArtist(artist)
        .setDuration((durationSeconds ?: 0).toLong())
        .setThumbnail(thumbnail.orEmpty())
        .build()

    private fun Wire.TrackInfo.ours(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        thumbnail = thumbnail.takeIf { it.isNotBlank() },
        durationSeconds = duration.toInt().takeIf { it > 0 },
    )
}
