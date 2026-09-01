package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether there is a newer one.
 *
 * It asks and then tells you; it does not fetch, unpack or replace anything. An
 * application that rewrites itself on a machine somebody else administers is a
 * different kind of program, and on Linux it would be fighting the package
 * manager that put it there.
 *
 * Off by default. A program that phones home on every launch should have been
 * asked first, and the answer to "is there a new version" is almost never
 * urgent.
 */
object Updates {

    /** Where releases are published. */
    private const val OWNER = "rajendra7169"
    private const val REPO = "blazify-desktop"

    /** What this build calls itself. Kept beside the packaging version. */
    const val RUNNING = "1.0.5"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 15_000 }
            expectSuccess = false
        }
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val store = File(Store.folder, "updates")

    var checkOnStart by mutableStateOf(false)
        private set

    var checking by mutableStateOf(false)
        private set

    /** The newest published version, once it has been asked for. */
    var latest by mutableStateOf<String?>(null)
        private set
    var notes by mutableStateOf<String?>(null)
        private set
    var link by mutableStateOf<String?>(null)
        private set
    var outcome by mutableStateOf<String?>(null)
        private set

    val newer: Boolean get() = latest?.let { isNewer(it, RUNNING) } == true

    init {
        runCatching { checkOnStart = store.readText().trim() == "true" }
        if (checkOnStart) check()
    }

    fun chooseCheckOnStart(value: Boolean) {
        checkOnStart = value
        runCatching { store.writeText(value.toString()) }
    }

    fun check() {
        if (checking) return
        checking = true
        outcome = null
        scope.launch {
            val body = runCatching {
                client.get("https://api.github.com/repos/$OWNER/$REPO/releases/latest") {
                    header("Accept", "application/vnd.github+json")
                    header("User-Agent", "Blazify")
                }.bodyAsText()
            }.getOrNull()

            checking = false
            if (body == null) {
                outcome = "Couldn't reach the release page."
                return@launch
            }

            val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            val release = when (root) {
                is JsonObject -> root
                // A repository with no releases answers with a message rather
                // than a release, and a list endpoint would answer with an
                // empty array. Both mean the same thing here.
                is JsonArray -> root.firstOrNull() as? JsonObject
                else -> null
            }

            val tag = release?.get("tag_name")?.plain()
            if (tag == null) {
                outcome = "No releases published yet."
                return@launch
            }

            latest = tag.removePrefix("v")
            notes = release["body"]?.plain()?.trim()?.takeIf { it.isNotBlank() }
            link = release["html_url"]?.plain()
            outcome = if (newer) {
                "Version $latest is out — you're on $RUNNING."
            } else {
                "You're on the newest version."
            }
        }
    }

    /**
     * Compare two dotted versions.
     *
     * Number by number rather than as text, because "1.10.0" is newer than
     * "1.9.0" and any string comparison says the opposite. Anything unparseable
     * counts as zero, so a tag somebody typed oddly can't announce itself as an
     * update.
     */
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (at in 0 until maxOf(a.size, b.size)) {
            val one = a.getOrElse(at) { 0 }
            val two = b.getOrElse(at) { 0 }
            if (one != two) return one > two
        }
        return false
    }

    /** Open the release page in whatever browser this machine uses. */
    fun openReleases() {
        val where = link ?: "https://github.com/$OWNER/$REPO/releases"
        runCatching {
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)
            if (windows) ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", where).start()
            else ProcessBuilder("xdg-open", where).start()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.plain(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
