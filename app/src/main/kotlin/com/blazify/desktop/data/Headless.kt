package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Borrowing the browser engine that is already on this machine.
 *
 * YouTube will not part with audio until whatever is asking has run a piece of
 * its own JavaScript and can show the answer — a scrambled address to unscramble
 * and a token that says the request came from a real player rather than a
 * script. Both of those are browser work, and a music player is not a browser.
 *
 * The two ways out of that are shipping a browser engine inside this
 * application, which is a hundred megabytes of Chromium for the sake of running
 * a few hundred lines of somebody else's script, or borrowing one — every
 * machine this runs on already has one, and Chromium-based browsers can be
 * asked to run headless and driven over a local socket. This borrows.
 *
 * Nothing of the person's own browsing is touched: a private profile is made in
 * this application's own folder, the window is never shown, and the process is
 * started when a song needs it and ends with the application. If there is no
 * such browser on the machine, that is said plainly rather than looking like a
 * song that won't play.
 */
object Headless {

    /** The browser being borrowed, by name, for settings to show. */
    var borrowed by mutableStateOf<String?>(null)
        private set

    /** Why it could not be borrowed, if it couldn't. */
    var trouble by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { HttpClient(OkHttp) { install(WebSockets) } }
    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Mutex()
    private val counter = AtomicInteger(0)

    private var process: Process? = null
    private var socket: DefaultClientWebSocketSession? = null
    private var pump: Job? = null

    /** Replies waiting to be matched to the call that asked for them. */
    private val waiting = mutableMapOf<Int, CompletableDeferred<JsonObject>>()

    /** Who wants to hear about what the page does, by event name. */
    private val listeners = mutableMapOf<String, MutableList<(JsonObject) -> Unit>>()

    /**
     * Which browser to borrow, or none.
     *
     * The same list the sign-in window opens, minus the Firefox family: only
     * the Chromium ones can be driven this way, and that list already knows
     * where Windows hides its programs. Two lists of browsers in one
     * application is one list that will be wrong.
     */
    private fun find(): SignInWindow.Opener? =
        SignInWindow.openers().firstOrNull { it.kind == BrowserSession.Kind.Chromium }

    /** Whether there is a browser to borrow at all, without starting one. */
    fun possible(): Boolean = find() != null

    /**
     * Start it if it isn't running, and answer whether it can be talked to.
     *
     * Started with nothing of its own: no window, no sound, no extensions, and a
     * profile folder that belongs to this application. It is left running
     * afterwards, because starting a browser is the expensive part and every
     * song after the first should not pay it again.
     */
    suspend fun ready(): Boolean = gate.withLock {
        if (socket != null && process?.isAlive == true) return@withLock true
        stopInternal()

        val opener = find() ?: run {
            trouble = "No Chromium-based browser on this machine — " +
                "Chromium, Chrome, Brave or Edge is what this needs, and any of them will do"
            return@withLock false
        }

        val profile = File(Store.folder, "engine").apply { mkdirs() }
        // Chromium writes the port it settled on into this file, and refuses to
        // start a second time if an old one is lying around claiming a port
        // nothing is listening on.
        val portFile = File(profile, "DevToolsActivePort").also { it.delete() }
        // A browser killed rather than asked to leave keeps its claim on the
        // profile, and the next one refuses to start at all rather than risk two
        // of them in one folder — "Failed to create a ProcessSingleton", then
        // nothing. The claim is only ever ours: one copy of this application
        // runs at a time and no person ever opens this profile.
        listOf("SingletonLock", "SingletonSocket", "SingletonCookie").forEach {
            runCatching { File(profile, it).delete() }
        }

        val started = runCatching {
            ProcessBuilder(
                opener.program,
                "--headless=new",
                "--remote-debugging-port=0",
                "--user-data-dir=${profile.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-extensions",
                "--disable-gpu",
                "--mute-audio",
                "--disable-background-networking",
                "--window-size=1280,720",
                "about:blank",
            ).redirectErrorStream(true).start()
        }.getOrElse {
            trouble = "${opener.label} wouldn't start: ${it.message}"
            return@withLock false
        }
        process = started
        // Nothing reads what it prints, and a pipe nobody empties is a pipe that
        // fills and stops the program writing to it.
        scope.launch { runCatching { started.inputStream.use { it.readBytes() } } }

        val port = withTimeoutOrNull(20_000) {
            while (true) {
                val line = runCatching { portFile.readLines().firstOrNull()?.toIntOrNull() }.getOrNull()
                if (line != null) return@withTimeoutOrNull line
                if (!started.isAlive) return@withTimeoutOrNull null
                delay(120)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        if (port == null) {
            trouble = "${opener.label} started but never said where to reach it"
            stopInternal()
            return@withLock false
        }

        val target = runCatching {
            val listing = client.get("http://127.0.0.1:$port/json/list").bodyAsText()
            json.parseToJsonElement(listing).let { element ->
                element.jsonArrayOrNull()
                    ?.map { it.jsonObject }
                    ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "page" }
                    ?.get("webSocketDebuggerUrl")?.jsonPrimitive?.content
            }
        }.getOrNull()
        if (target == null) {
            trouble = "${opener.label} is running but offered no page to work in"
            stopInternal()
            return@withLock false
        }

        val session = runCatching { client.webSocketSession(target) }.getOrNull()
        if (session == null) {
            trouble = "Couldn't connect to ${opener.label}"
            stopInternal()
            return@withLock false
        }
        socket = session
        pump = scope.launch {
            runCatching {
                session.incoming.consumeEach { frame ->
                    if (frame is Frame.Text) heard(frame.readText())
                }
            }
            // The line dropping is not itself a failure worth reporting: the
            // next thing that needs the engine starts it again.
            gate.withLock { if (socket === session) stopInternal() }
        }
        borrowed = opener.label
        trouble = null
        true
    }

    /** Sort a reply to whoever asked, or an event to whoever is listening. */
    private fun heard(text: String) {
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val id = message["id"]?.jsonPrimitive?.intOrNull()
        if (id != null) {
            val waiter = synchronized(waiting) { waiting.remove(id) }
            waiter?.complete(message["result"]?.jsonObject ?: JsonObject(emptyMap()))
            return
        }
        val method = message["method"]?.jsonPrimitive?.content ?: return
        val params = message["params"]?.jsonObject ?: JsonObject(emptyMap())
        val heard = synchronized(listeners) { listeners[method]?.toList() }.orEmpty()
        heard.forEach { runCatching { it(params) } }
    }

    /** Be told when the page does something, for as long as the engine lives. */
    fun listen(method: String, handler: (JsonObject) -> Unit) {
        synchronized(listeners) { listeners.getOrPut(method) { mutableListOf() } += handler }
    }

    /**
     * Ask the engine to do something and wait for its answer.
     *
     * A call that never comes back would hang the song rather than fail it, so
     * everything here is given a deadline and a missed deadline is an answer.
     */
    suspend fun send(method: String, params: JsonObject = JsonObject(emptyMap()), waitMs: Long = 30_000): JsonObject? {
        if (!ready()) return null
        val session = socket ?: return null
        val id = counter.incrementAndGet()
        val reply = CompletableDeferred<JsonObject>()
        synchronized(waiting) { waiting[id] = reply }
        val body = buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val sent = runCatching { session.send(Frame.Text(body.toString())) }.isSuccess
        if (!sent) {
            synchronized(waiting) { waiting.remove(id) }
            return null
        }
        return withTimeoutOrNull(waitMs) { reply.await() }
            ?: null.also { synchronized(waiting) { waiting.remove(id) } }
    }

    /**
     * Run a piece of JavaScript in the page and bring the value back.
     *
     * Values come back as themselves rather than as handles to something inside
     * the engine, because everything asked for here is a string — an address, a
     * token — and a handle would have to be fetched a second time to be read.
     */
    suspend fun evaluate(js: String, waitMs: Long = 30_000): String? {
        val result = send(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", js)
                put("returnByValue", true)
                put("awaitPromise", true)
                put("timeout", waitMs.toDouble())
            },
            waitMs,
        ) ?: return null
        if (result["exceptionDetails"] != null) return null
        val value = result["result"]?.jsonObject?.get("value") ?: return null
        return runCatching { value.jsonPrimitive.content }.getOrNull()
    }

    /**
     * Let go of the browser. Called when the application closes.
     *
     * Asked to leave before being made to: a browser told to close puts its
     * profile down tidily, and one that is killed leaves its claim on it behind
     * for the next start to trip over.
     */
    fun stop() {
        scope.launch {
            withTimeoutOrNull(4_000) { send("Browser.close", waitMs = 3_000) }
            gate.withLock { stopInternal() }
        }
    }

    private fun stopInternal() {
        pump?.cancel()
        pump = null
        runCatching { socket?.cancel() }
        socket = null
        synchronized(waiting) {
            waiting.values.forEach { it.cancel() }
            waiting.clear()
        }
        process?.let { running ->
            runCatching { running.destroy() }
            // A browser that ignores being asked is a browser left running after
            // the application it belonged to has gone.
            if (!running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                runCatching { running.destroyForcibly() }
            }
        }
        process = null
    }

    init {
        // Whatever else happens on the way out, the borrowed browser goes with
        // it — including a crash, where nothing else here gets a say.
        Runtime.getRuntime().addShutdownHook(Thread { stopInternal() })
    }
}

/** A JSON array, or nothing, without throwing on the way. */
private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull(): kotlinx.serialization.json.JsonArray? =
    this as? kotlinx.serialization.json.JsonArray

/** An int, or nothing, from a value that may be a string or absent. */
private fun kotlinx.serialization.json.JsonPrimitive.intOrNull(): Int? = content.toIntOrNull()
