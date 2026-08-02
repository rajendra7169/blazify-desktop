package com.blazify.desktop.tools.room

import com.blazify.desktop.together.Say
import com.blazify.desktop.together.Servers
import com.blazify.desktop.together.proto.Wire
import com.google.protobuf.ByteString
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Open a room from a terminal and print everything the server says back.
 *
 * The room server is the one part of this app whose behaviour is somebody
 * else's, so when a room won't form the useful question is not "what does our
 * code do" but "what did the server actually reply". This prints that.
 */
fun main(args: Array<String>): Unit = runBlocking {
    // `roomProbe` hosts; `roomProbe --args="join CODE"` knocks on one.
    val joining = args.getOrNull(0) == "join"
    val room = args.getOrNull(1).orEmpty()
    val url = Servers.DEFAULT
    println("dialling $url")

    val client = HttpClient(OkHttp) { install(WebSockets) }
    val session = client.webSocketSession(url)
    println("open")

    launch {
        session.incoming.consumeEachFrame { bytes ->
            val envelope = runCatching { Wire.Envelope.parseFrom(bytes) }.getOrNull()
            if (envelope == null) {
                println("<- ${bytes.size} bytes, not an envelope: ${bytes.take(40)}")
                return@consumeEachFrame
            }
            val body = envelope.payload.toByteArray()
            print("<- ${envelope.type} (${body.size}b)")
            when (envelope.type) {
                Say.ROOM_CREATED -> Wire.RoomCreatedPayload.parseFrom(body).let {
                    print("  code=${it.roomCode} user=${it.userId} token=${it.sessionToken.take(12)}…")
                }
                Say.ERROR -> Wire.ErrorPayload.parseFrom(body).let {
                    print("  ${it.code}: ${it.message}")
                }
                Say.JOIN_REQUEST -> Wire.JoinRequestPayload.parseFrom(body).let {
                    print("  ${it.username} (${it.userId})")
                }
                Say.USER_JOINED -> Wire.UserJoinedPayload.parseFrom(body).let {
                    print("  ${it.username}")
                }
                Say.JOIN_APPROVED -> Wire.JoinApprovedPayload.parseFrom(body).let {
                    print("  in room ${it.roomCode} as ${it.userId}")
                }
                Say.JOIN_REJECTED -> Wire.JoinRejectedPayload.parseFrom(body).let {
                    print("  refused: ${it.reason}")
                }
            }
            println()
        }
    }

    fun send(type: String, payload: com.google.protobuf.MessageLite?) {
        val envelope = Wire.Envelope.newBuilder()
            .setType(type)
            .setPayload(payload?.let { ByteString.copyFrom(it.toByteArray()) } ?: ByteString.EMPTY)
            .setCompressed(false)
            .build()
        println("-> $type")
        launch { session.send(Frame.Binary(true, envelope.toByteArray())) }
    }

    if (joining) {
        send(
            Say.JOIN_ROOM,
            Wire.JoinRoomPayload.newBuilder().setRoomCode(room).setUsername("ProbeGuest").build(),
        )
    } else {
        send(Say.CREATE_ROOM, Wire.CreateRoomPayload.newBuilder().setUsername("ProbeHost").build())
    }

    // Long enough to see a room form and for somebody to try the code.
    delay(45_000)
    println("done")
    session.close()
}

private suspend inline fun kotlinx.coroutines.channels.ReceiveChannel<Frame>.consumeEachFrame(
    crossinline onBinary: (ByteArray) -> Unit,
) {
    for (frame in this) {
        when (frame) {
            is Frame.Binary -> onBinary(frame.readBytes())
            is Frame.Text -> println("<- text: ${frame.readText()}")
            else -> Unit
        }
    }
}
