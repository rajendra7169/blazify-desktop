package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Telling the chat app what you're listening to.
 *
 * It talks to the client already running on this machine rather than to any
 * server — a named pipe on Windows, a socket in the runtime directory on Linux.
 * That means no account, no token and no login here: if the client is open the
 * presence appears, and if it isn't, nothing happens and nothing is sent
 * anywhere.
 *
 * Off by default. Announcing what somebody is listening to, to everyone who can
 * see them, is not a thing to switch on for them.
 */
object Presence {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = File(Store.folder, "presence")

    /**
     * The application this presence belongs to.
     *
     * Editable because the name and artwork shown beside the song come from
     * whichever application id is used, and somebody running their own build
     * may well want their own.
     */
    var appId by mutableStateOf("1447278780795064401")
        private set

    var enabled by mutableStateOf(false)
        private set

    /** Whether the album cover is sent along with the names. */
    var showArtwork by mutableStateOf(true)
        private set

    /** Whether a link back to the song is offered as a button. */
    var showLink by mutableStateOf(true)
        private set

    var connected by mutableStateOf(false)
        private set
    var trouble by mutableStateOf<String?>(null)
        private set

    private var pipe: AutoCloseable? = null
    private var channel: SocketChannel? = null
    private var windowsPipe: RandomAccessFile? = null

    init {
        runCatching {
            if (!store.exists()) return@runCatching
            val lines = store.readLines()
            enabled = lines.getOrNull(0) == "true"
            appId = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: appId
            showArtwork = lines.getOrNull(2) != "false"
            showLink = lines.getOrNull(3) != "false"
        }
        if (enabled) scope.launch { open() }
    }

    private fun save() {
        runCatching {
            store.writeText(
                listOf(enabled.toString(), appId, showArtwork.toString(), showLink.toString())
                    .joinToString("\n"),
            )
        }
    }

    fun choose(value: Boolean) {
        enabled = value
        save()
        scope.launch { if (value) open() else close() }
    }

    fun chooseAppId(value: String) {
        appId = value.trim()
        save()
        if (enabled) scope.launch { close(); open() }
    }

    fun chooseArtwork(value: Boolean) { showArtwork = value; save() }
    fun chooseLink(value: Boolean) { showLink = value; save() }

    // ── the pipe ─────────────────────────────────────────────────────────────

    /**
     * Where the client listens.
     *
     * Up to ten of them can be open at once, numbered — the first that answers
     * is the one to use. On Linux the directory depends on how the client was
     * installed, which is why several are tried rather than one.
     */
    private fun candidates(): List<String> {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)
        if (windows) return (0..9).map { """\\.\pipe\discord-ipc-$it""" }

        val roots = listOfNotNull(
            System.getenv("XDG_RUNTIME_DIR"),
            System.getenv("TMPDIR"),
            "/tmp",
        ).distinct()
        // Flatpak and Snap put the socket a level or two deeper.
        val nests = listOf("", "app/com.discordapp.Discord/", "snap.discord/")
        return roots.flatMap { root ->
            nests.flatMap { nest -> (0..9).map { "$root/$nest" + "discord-ipc-$it" } }
        }
    }

    private fun open(): Boolean {
        if (connected) return true
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)

        for (path in candidates()) {
            val opened = runCatching {
                if (windows) {
                    if (!File(path).exists()) return@runCatching false
                    windowsPipe = RandomAccessFile(path, "rw")
                    true
                } else {
                    val file = File(path)
                    if (!file.exists()) return@runCatching false
                    channel = SocketChannel.open(StandardProtocolFamily.UNIX).apply {
                        connect(UnixDomainSocketAddress.of(file.toPath()))
                    }
                    true
                }
            }.getOrDefault(false)

            if (opened) {
                // Say hello, or every frame after this is ignored.
                val hello = buildJsonObject {
                    put("v", 1)
                    put("client_id", appId)
                }
                if (write(0, hello.toString())) {
                    connected = true
                    trouble = null
                    return true
                }
                close()
            }
        }
        trouble = "No running Discord found on this computer."
        return false
    }

    private fun close() {
        runCatching { channel?.close() }
        runCatching { windowsPipe?.close() }
        runCatching { pipe?.close() }
        channel = null
        windowsPipe = null
        pipe = null
        connected = false
    }

    /**
     * One frame: an opcode, a length, then the JSON.
     *
     * Both numbers are little-endian regardless of the machine, because the
     * client on the other end decided that and it is not ours to argue with.
     */
    private fun write(opcode: Int, body: String): Boolean = runCatching {
        val payload = body.toByteArray(Charsets.UTF_8)
        val frame = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        frame.putInt(opcode)
        frame.putInt(payload.size)
        frame.put(payload)
        frame.flip()

        val out = windowsPipe
        if (out != null) {
            out.write(frame.array())
        } else {
            val socket = channel ?: return@runCatching false
            while (frame.hasRemaining()) socket.write(frame)
        }
        true
    }.getOrElse {
        close()
        false
    }

    // ── what it says ─────────────────────────────────────────────────────────

    /**
     * Show a song, or clear the presence when there isn't one.
     *
     * The end time is sent rather than a countdown, so the client does the
     * ticking — a message per second to say the same song is still playing
     * would be a message per second.
     */
    fun show(track: Track?, playing: Boolean, positionSeconds: Double) {
        if (!enabled) return
        scope.launch {
            if (!connected && !open()) return@launch

            if (track == null || !playing) {
                send(buildJsonObject { put("pid", handle()) })
                return@launch
            }

            val activity = buildJsonObject {
                // Type 2 is "Listening to", which is what this is.
                put("type", 2)
                put("details", track.title.take(128))
                put("state", track.artist.take(128).ifBlank { "Unknown artist" })

                track.durationSeconds?.takeIf { it > 0 }?.let { length ->
                    val now = System.currentTimeMillis()
                    put(
                        "timestamps",
                        buildJsonObject {
                            put("start", now - (positionSeconds * 1000).toLong())
                            put("end", now + ((length - positionSeconds) * 1000).toLong())
                        },
                    )
                }

                if (showArtwork) {
                    put(
                        "assets",
                        buildJsonObject {
                            put("large_image", track.thumbnail ?: "blazify")
                            put("large_text", track.title.take(128))
                        },
                    )
                }

                if (showLink && !LocalMusic.isLocal(track.id)) {
                    put(
                        "buttons",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("label", "Listen")
                                    put("url", "https://music.youtube.com/watch?v=${track.id}")
                                },
                            )
                        },
                    )
                }
            }

            send(
                buildJsonObject {
                    put("pid", handle())
                    put("activity", activity)
                },
            )
        }
    }

    private fun send(args: kotlinx.serialization.json.JsonObject) {
        val frame = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("args", args)
            put("nonce", java.util.UUID.randomUUID().toString())
        }
        write(1, frame.toString())
    }

    private fun handle(): Int = runCatching {
        ProcessHandle.current().pid().toInt()
    }.getOrDefault(0)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.put(key: String, value: Int) {
    put(key, JsonPrimitive(value))
}
