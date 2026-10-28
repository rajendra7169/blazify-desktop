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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether there is a newer one, and — asked to — fetching it and putting it in.
 *
 * The version is asked for from Blazify's own site first and from the release
 * page second, and the files come from a mirror first and the release page
 * second: one host going quiet, or turning a project away, leaves the other to
 * answer. Every file is tried at each of its addresses in turn, and a download
 * that drops is picked up where it stopped.
 *
 * On by default: a look at one small file each time Blazify opens, which is
 * the only way someone who never visits Settings ever hears of a fix. It can be
 * turned off there, and that choice is kept. Nothing is downloaded until
 * somebody presses the button.
 */
object Updates {

    /** Where releases are published. */
    private const val OWNER = "rajendra7169"
    private const val REPO = "blazify-desktop"

    /** Blazify's own description of the newest version, kept beside its website. */
    private const val MANIFEST = "https://rajendrapandey.info.np/blazify/desktop.json"

    /** What this build calls itself. Kept beside the packaging version. */
    const val RUNNING = "1.4.0"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 15_000 }
            expectSuccess = false
        }
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val store = File(Store.folder, "updates")

    /** One file of a release, and every address it can be fetched from, in order. */
    data class Download(val name: String, val size: Long?, val urls: List<String>)

    var checkOnStart by mutableStateOf(true)
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

    /** The files of the newest release. */
    private var files: List<Download> = emptyList()

    /** How far a download has got, from 0 to 1, while there is one. */
    var progress by mutableStateOf<Float?>(null)
        private set

    /** The installer, fetched and whole, ready to be put in. */
    var ready by mutableStateOf<File?>(null)
        private set

    /** Bytes of the download so far, and in all, for the popup to show. */
    var received by mutableStateOf(0L)
        private set
    var total by mutableStateOf<Long?>(null)
        private set

    /** Why the last download stopped, while the popup shows it. */
    var failure by mutableStateOf<String?>(null)
        private set

    /** Whether the update popup is up. */
    var showing by mutableStateOf(false)
        private set

    @Volatile private var cancelled = false

    fun hide() {
        showing = false
    }

    /** Stops a download part way; what has arrived is kept for the next try. */
    fun cancel() {
        cancelled = true
        showing = false
    }

    val newer: Boolean get() = latest?.let { isNewer(it, RUNNING) } == true

    /** The file of the newest release this copy of Blazify can install, if there is one. */
    val download: Download? get() = UpdateInstall.pick(files)

    init {
        runCatching {
            File(folder(), "check-on-start").takeIf { it.exists() }?.let { checkOnStart = it.readText().trim() != "false" }
        }
        if (checkOnStart) check()
    }

    /**
     * The folder for updates. An older build kept the one setting here in a file of this name,
     * so a file found in its place is turned into the folder, keeping the setting.
     */
    private fun folder(): File {
        // A file of this name is the setting an older build kept, when checking was off unless
        // asked for. That answer is let go, so checking starts on like everybody else's.
        if (store.isFile) store.delete()
        store.mkdirs()
        return store
    }

    fun chooseCheckOnStart(value: Boolean) {
        checkOnStart = value
        runCatching { File(folder(), "check-on-start").writeText(value.toString()) }
    }

    fun check() {
        if (checking) return
        checking = true
        outcome = null
        scope.launch {
            val found = fromManifest() ?: fromReleasePage()
            checking = false
            if (found == null) {
                outcome = "Couldn't reach the release page."
                return@launch
            }
            if (!found) {
                outcome = "No releases published yet."
                return@launch
            }
            outcome = if (newer) {
                "Version $latest is out — you're on $RUNNING."
            } else {
                "You're on the newest version."
            }
        }
    }

    /** True when the site answered with a release, false when with none, null when not at all. */
    private suspend fun fromManifest(): Boolean? {
        val body = runCatching {
            // BLAZIFY_UPDATE_MANIFEST points a test build at an update file of its own.
            client.get(System.getenv("BLAZIFY_UPDATE_MANIFEST") ?: MANIFEST) { header("User-Agent", "Blazify") }
                .takeIf { it.status.value == 200 }?.bodyAsText()
        }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val version = root["version"]?.plain() ?: return false
        latest = version.removePrefix("v")
        notes = root["notes"]?.plain()?.trim()?.takeIf { it.isNotBlank() }
        link = root["page"]?.plain()
        files = (root["files"] as? JsonArray).orEmpty().mapNotNull { element ->
            val file = element as? JsonObject ?: return@mapNotNull null
            val name = file["name"]?.plain() ?: return@mapNotNull null
            val urls = (file["urls"] as? JsonArray).orEmpty().mapNotNull { it.plain() }
            if (urls.isEmpty()) return@mapNotNull null
            Download(name, file["size"]?.plain()?.toLongOrNull(), urls)
        }
        return true
    }

    /** The same, asked of the release page itself. */
    private suspend fun fromReleasePage(): Boolean? {
        val body = runCatching {
            client.get("https://api.github.com/repos/$OWNER/$REPO/releases/latest") {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "Blazify")
            }.bodyAsText()
        }.getOrNull() ?: return null

        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val release = when (root) {
            is JsonObject -> root
            // A repository with no releases answers with a message rather
            // than a release, and a list endpoint would answer with an
            // empty array. Both mean the same thing here.
            is JsonArray -> root.firstOrNull() as? JsonObject
            else -> null
        }

        val tag = release?.get("tag_name")?.plain() ?: return false
        latest = tag.removePrefix("v")
        notes = release["body"]?.plain()?.trim()?.takeIf { it.isNotBlank() }
        link = release["html_url"]?.plain()
        files = (release["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val name = asset["name"]?.plain() ?: return@mapNotNull null
            val url = asset["browser_download_url"]?.plain() ?: return@mapNotNull null
            Download(name, asset["size"]?.plain()?.toLongOrNull(), listOf(url))
        }
        return true
    }

    /**
     * Fetches the installer for this machine, with its progress shown as it goes.
     *
     * Each address is given a few tries and then the next is tried, and a try
     * that drops carries on from the bytes already saved rather than starting
     * again. A file whose size the release gave has to arrive at that size.
     */
    fun fetch() {
        showing = true
        val want = download ?: return
        if (progress != null || ready != null) return
        progress = 0f
        failure = null
        cancelled = false
        total = want.size
        outcome = "Downloading Blazify $latest…"
        scope.launch {
            val done = runCatching { fetchFile(want) }
                .onFailure {
                    failure = if (cancelled) null else (it.message ?: "no reason given")
                    outcome = if (cancelled) null else "The download stopped. Try again."
                }
                .getOrNull()
            progress = null
            if (done != null) {
                ready = done
                outcome = "Blazify $latest is downloaded and ready to install."
            }
        }
    }

    private fun fetchFile(want: Download): File {
        val folder = File(folder(), latest ?: "new").apply { mkdirs() }
        val target = File(folder, want.name)
        if (want.size != null && target.length() == want.size) return target
        val part = File(folder, want.name + ".part")
        var last: Exception? = null
        for (url in want.urls) {
            repeat(TRIES_PER_ADDRESS) {
                if (cancelled) error("cancelled")
                try {
                    fetchInto(url, part, want.size)
                    if (want.size == null || part.length() == want.size) {
                        target.delete()
                        if (!part.renameTo(target)) error("Couldn't put the download in place")
                        return target
                    }
                } catch (e: Exception) {
                    last = e
                }
            }
        }
        throw last ?: IllegalStateException("No address answered")
    }

    private fun fetchInto(url: String, part: File, size: Long?) {
        val have = part.length()
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Blazify")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = connection.responseCode
            val resuming = code == 206
            if (code != 200 && !resuming) error("the server answered $code")
            // A server that ignores the range sends the whole file again: start the file over.
            val append = resuming && have > 0
            val total = size ?: connection.getHeaderField("Content-Length")?.toLongOrNull()?.let { if (append) it + have else it }
            this.total = total
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var got = if (append) have else 0L
                    while (true) {
                        if (cancelled) error("cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        got += read
                        received = got
                        if (total != null && total > 0) progress = (got.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Closes Blazify and puts the downloaded version in; it opens again when that is done. */
    fun install() {
        val file = ready ?: return
        outcome = UpdateInstall.start(file) ?: return
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
        Browse.open(link ?: "https://github.com/$OWNER/$REPO/releases")
    }

    private const val TRIES_PER_ADDRESS = 3

    private fun kotlinx.serialization.json.JsonElement.plain(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
