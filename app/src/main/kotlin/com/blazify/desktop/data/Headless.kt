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
import kotlinx.coroutines.runBlocking
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

    /**
     * True while the application is closing.
     *
     * Asking the browser to close goes through the same door as everything else,
     * and that door starts a browser when there isn't one — which would mean
     * shutting down by starting a browser and leaving it behind.
     */
    @Volatile
    private var leaving = false

    /** Replies waiting to be matched to the call that asked for them. */
    private val waiting = mutableMapOf<Int, CompletableDeferred<JsonObject>>()

    /** Who wants to hear about what the page does, by event name. */
    private val listeners = mutableMapOf<String, MutableList<(JsonObject) -> Unit>>()

    /** One thing worth trying, and what to call it if it works. */
    private data class Candidate(val label: String, val command: List<String>)

    /**
     * Chromium under names this application has never heard of.
     *
     * The engine is the same in all of them, and somebody running a fork is not
     * running a worse browser — they are running one whose name nobody thought
     * to write down. Every one of these is Chromium wearing a different badge.
     */
    private val ALSO = listOf(
        "thorium-browser", "thorium", "ungoogled-chromium", "chromium-freeworld",
        "brave-browser-stable", "brave-browser-beta", "google-chrome-beta",
        "google-chrome-unstable", "microsoft-edge-beta", "microsoft-edge-dev",
        "vivaldi-stable", "vivaldi-snapshot", "opera-beta", "yandex-browser",
        "chrome", "chromium-bin",
    )

    /** Where package formats that aren't on the path put things. */
    private val ELSEWHERE = listOf(
        "/snap/bin/chromium", "/snap/bin/brave", "/snap/bin/chromium-browser",
        "/usr/lib/chromium/chromium", "/usr/lib/chromium-browser/chromium-browser",
        "/opt/google/chrome/chrome", "/opt/brave.com/brave/brave",
        "/opt/microsoft/msedge/msedge", "/opt/vivaldi/vivaldi-bin",
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
        "/Applications/Chromium.app/Contents/MacOS/Chromium",
        "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    )

    /** Flatpak keeps its programs off the path entirely, behind its own runner. */
    private val FLATPAKS = listOf(
        "com.brave.Browser", "com.google.Chrome", "org.chromium.Chromium",
        "com.microsoft.Edge", "com.vivaldi.Vivaldi", "com.opera.Opera",
    )

    /**
     * Everything worth trying, best first.
     *
     * Deliberately generous, because the alternative is telling somebody with a
     * perfectly good browser that they haven't got one. A name being on this
     * list is not a claim that it works — the only honest test is starting it
     * and seeing whether it answers, which is what [ready] does, one candidate
     * after another. The one that answered last time goes first, so the usual
     * case is one attempt.
     */
    private fun candidates(): List<Candidate> {
        val found = LinkedHashMap<String, Candidate>()
        fun offer(label: String, command: List<String>) {
            val key = command.joinToString(" ")
            if (key.isNotBlank()) found.putIfAbsent(key, Candidate(label, command))
        }

        // What somebody pointed at themselves outranks everything: they know
        // what is on their machine better than any list does.
        chosen?.takeIf { it.isNotBlank() }?.let { offer("the browser you chose", it.split(" ")) }
        // Then whatever worked last time, which saves trying the rest again.
        remembered()?.let { offer(it.first, it.second.split(" ")) }

        // The list the sign-in window already keeps, minus the Firefox family:
        // only the Chromium ones can be driven this way.
        SignInWindow.openers()
            .filter { it.kind == BrowserSession.Kind.Chromium }
            .forEach { offer(it.label, listOf(it.program)) }

        if (!onWindows) {
            ALSO.forEach { name -> which(name)?.let { offer(pretty(name), listOf(it)) } }
            ELSEWHERE.forEach { path -> if (File(path).canExecute()) offer(pretty(File(path).name), listOf(path)) }
            // The browser this machine opens links with, whatever it is called.
            // This is what catches a fork nobody has heard of.
            systemDefault()?.let { offer(pretty(File(it.first()).name), it) }
            installedFlatpaks().forEach { id -> offer(pretty(id.substringAfterLast('.')), listOf("flatpak", "run", id)) }
        }
        return found.values.toList()
    }

    private val onWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** A name fit to show somebody, out of a program name. */
    private fun pretty(name: String): String = name
        .removeSuffix(".exe").removeSuffix("-bin").removeSuffix("-browser")
        .removeSuffix("-stable").split('-', '.').joinToString(" ") {
            it.replaceFirstChar { first -> first.uppercase() }
        }.trim()

    private fun which(program: String): String? = runCatching {
        val process = ProcessBuilder("which", program).redirectErrorStream(true).start()
        val path = process.inputStream.bufferedReader().readText().trim().lines().firstOrNull().orEmpty()
        if (process.waitFor() == 0 && path.isNotBlank() && File(path).canExecute()) path else null
    }.getOrNull()

    /**
     * The program behind whatever this machine opens web links with.
     *
     * The desktop names it as a launcher file rather than a program, and that
     * file says how to run it — which may be a flatpak runner, a wrapper script
     * or a path nobody would have guessed. Reading it is how an unheard-of
     * browser gets a fair chance.
     */
    private fun systemDefault(): List<String>? = runCatching {
        val name = ProcessBuilder("xdg-settings", "get", "default-web-browser")
            .redirectErrorStream(true).start().let {
                val text = it.inputStream.bufferedReader().readText().trim()
                if (it.waitFor() == 0) text else ""
            }
        if (!name.endsWith(".desktop")) return null

        val homes = listOf(
            File(System.getProperty("user.home"), ".local/share/applications"),
            File("/usr/share/applications"),
            File("/var/lib/flatpak/exports/share/applications"),
            File(System.getProperty("user.home"), ".local/share/flatpak/exports/share/applications"),
        )
        val entry = homes.map { File(it, name) }.firstOrNull { it.isFile } ?: return null
        val exec = entry.readLines().firstOrNull { it.startsWith("Exec=") }?.removePrefix("Exec=") ?: return null
        // The launcher line carries placeholders for the address it was asked to
        // open, and quoting that is nobody's idea of fun. Neither belongs here.
        val words = exec.split(" ").filter { it.isNotBlank() && !it.startsWith("%") }.map { it.trim('"') }
        words.takeIf { it.isNotEmpty() && (File(it.first()).canExecute() || which(it.first()) != null) }
    }.getOrNull()

    private fun installedFlatpaks(): List<String> = runCatching {
        val process = ProcessBuilder("flatpak", "list", "--app", "--columns=application")
            .redirectErrorStream(true).start()
        val listed = process.inputStream.bufferedReader().readText().lines().map { it.trim() }
        if (process.waitFor() != 0) return emptyList()
        FLATPAKS.filter { it in listed }
    }.getOrDefault(emptyList())

    /** What somebody pointed this at themselves, if they had to. */
    var chosen by mutableStateOf<String?>(null)
        private set

    private val choiceStore: File get() = File(Store.folder, "engine-browser")

    /** Point it at a program by hand, for a machine whose browser is its own secret. */
    fun choose(program: String?) {
        chosen = program?.takeIf { it.isNotBlank() }
        runCatching {
            if (chosen == null) choiceStore.delete() else choiceStore.writeText(chosen!!)
        }
        // Whatever is running was started from the old answer.
        stop()
    }

    private fun remembered(): Pair<String, String>? = runCatching {
        val lines = File(Store.folder, "engine-worked").takeIf { it.exists() }?.readLines() ?: return null
        val label = lines.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val command = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        label to command
    }.getOrNull()

    private fun remember(candidate: Candidate) {
        runCatching {
            File(Store.folder, "engine-worked")
                .writeText(candidate.label + "\n" + candidate.command.joinToString(" "))
        }
    }

    /** Whether there is anything to try at all, without starting one. */
    fun possible(): Boolean = candidates().isNotEmpty()

    /**
     * Everything that would be tried, in order, for the probe to print.
     *
     * Worth being able to see from a terminal: "no browser found" on a machine
     * with three of them is a bug in the looking, and the only way to tell that
     * from the truth is to see the list.
     */
    fun candidatesForProbe(): List<Pair<String, List<String>>> =
        candidates().map { it.label to it.command }

    init {
        runCatching { chosen = choiceStore.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null } }
    }

    /**
     * Start something if nothing is running, and answer whether it can be
     * talked to.
     *
     * Every candidate gets a turn, because a name on a list is not proof: a
     * browser can be installed and still refuse, and the next one along may be
     * perfectly willing. The one that works is remembered, so the usual case
     * afterwards is a single attempt.
     *
     * It is left running, because starting a browser is the expensive part and
     * every song after the first should not pay it again.
     */
    suspend fun ready(): Boolean = gate.withLock {
        if (socket != null && process?.isAlive == true) return@withLock true
        if (leaving) return@withLock false
        stopInternal()

        val tries = candidates()
        if (tries.isEmpty()) {
            trouble = "No Chromium-based browser on this machine. Chromium, Chrome, Brave, Edge, " +
                "Vivaldi and Opera all work, and one that is here under another name can be " +
                "pointed at by hand in settings."
            return@withLock false
        }

        val refused = mutableListOf<String>()
        for (candidate in tries) {
            val why = launch(candidate)
            if (why == null) {
                borrowed = candidate.label
                trouble = null
                remember(candidate)
                return@withLock true
            }
            refused += "${candidate.label} — $why"
            stopInternal()
        }
        trouble = "None of the browsers here would lend their engine: ${refused.joinToString("; ")}"
        false
    }

    /**
     * Try one of them. Null means it worked; anything else is what went wrong.
     *
     * Started with nothing of its own: no window, no sound, no extensions, and a
     * profile folder belonging to this application rather than to the person.
     */
    private suspend fun launch(candidate: Candidate): String? {
        val profile = File(Store.folder, "engine").apply { mkdirs() }
        // Chromium writes the port it settled on into this file, and an old one
        // lying around claims a port nothing is listening on.
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
                candidate.command + listOf(
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
                ),
            ).redirectErrorStream(true).start()
        }.getOrElse { return "wouldn't start (${it.message})" }
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
        } ?: return "started but never said where to reach it"

        val target = runCatching {
            val listing = client.get("http://127.0.0.1:$port/json/list").bodyAsText()
            json.parseToJsonElement(listing).let { element ->
                element.jsonArrayOrNull()
                    ?.map { it.jsonObject }
                    ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "page" }
                    ?.get("webSocketDebuggerUrl")?.jsonPrimitive?.content
            }
        }.getOrNull() ?: return "offered no page to work in"

        val session = runCatching { client.webSocketSession(target) }.getOrNull()
            ?: return "wouldn't accept a connection"

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
        return null
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
     * Point the page at an address and wait until it has actually loaded.
     *
     * The reply to the navigation says the browser accepted the address, not
     * that there is a document to work in — so the document is asked itself.
     */
    suspend fun open(url: String, waitMs: Long = 20_000): Boolean {
        send("Page.navigate", buildJsonObject { put("url", url) }, waitMs) ?: return false
        val deadline = System.currentTimeMillis() + waitMs
        while (System.currentTimeMillis() < deadline) {
            if (evaluate("document.readyState", waitMs = 5_000) == "complete") return true
            delay(100)
        }
        return false
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
    fun stop() = runBlocking {
        leaving = true
        socket?.let { open ->
            runCatching {
                withTimeoutOrNull(3_000) {
                    open.send(Frame.Text("{\"id\":0,\"method\":\"Browser.close\"}"))
                    // Long enough for it to put the profile down tidily, not
                    // long enough to be noticed on the way out.
                    while (process?.isAlive == true) delay(50)
                }
            }
        }
        gate.withLock { stopInternal() }
        leaving = false
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
            // The children as well as the parent. A browser is a tree of
            // processes, and taking only the root down leaves the rest of it
            // sitting there holding the profile folder.
            val family = runCatching { running.descendants().toList() }.getOrDefault(emptyList())
            runCatching { running.destroy() }
            // A browser that ignores being asked is a browser left running after
            // the application it belonged to has gone.
            if (!running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                runCatching { running.destroyForcibly() }
            }
            family.forEach { child ->
                if (child.isAlive) {
                    runCatching { child.destroy() }
                    runCatching { child.destroyForcibly() }
                }
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
