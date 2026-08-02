package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.prefs.Preferences
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everything this app knows about you, in one file you can move.
 *
 * A new laptop, a reinstall, a second machine — all the same problem, and none
 * of them worth losing eight hundred liked songs over. The file is an ordinary
 * zip of ordinary JSON: openable, readable, and not dependent on this program
 * still existing to make sense of.
 *
 * The session is deliberately left out. A copy of it in a file that gets emailed
 * about is a copy of the account, and signing in again takes ten seconds.
 */
object Backup {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What goes in, and what each thing is called when it's put back. */
    private val PARTS = listOf(
        "liked.json",
        "history.json",
        "plays.json",
        "playlists.json",
        "saved.json",
        "local.json",
        "folders.json",
        "downloads.json",
    )

    /** Things that identify the account rather than describe the listening. */
    private val PRIVATE = setOf("account", "account-refresh", "together-session", "lastfm")

    private const val SETTINGS = "settings.properties"

    var busy by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<String?>(null)
        private set

    fun forget() { outcome = null }

    /** A sensible name for a file somebody will find in a downloads folder in a year. */
    fun suggestedName(): String = "blazify-backup-${LocalDate.now()}.zip"

    /**
     * Write everything out.
     *
     * Missing pieces are skipped rather than written empty — a backup taken
     * before you ever made a playlist shouldn't restore an empty playlist file
     * over one you have since made.
     */
    fun writeTo(target: File) {
        if (busy) return
        busy = true
        outcome = null
        scope.launch {
            val done = runCatching {
                ZipOutputStream(target.outputStream().buffered()).use { zip ->
                    PARTS.forEach { name ->
                        val file = File(Store.folder, name)
                        if (!file.exists()) return@forEach
                        zip.putNextEntry(ZipEntry(name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }

                    // The look, the lyrics settings, the keyboard — everything
                    // held as preferences rather than as a file of its own.
                    zip.putNextEntry(ZipEntry(SETTINGS))
                    zip.write(settingsAsText().toByteArray())
                    zip.closeEntry()
                }
            }
            busy = false
            outcome = done.fold(
                onSuccess = { "Saved to ${target.name}" },
                onFailure = { "Couldn't write that file — is the folder writable?" },
            )
        }
    }

    /**
     * Read one back.
     *
     * Entry names are checked against the list rather than trusted, because a
     * zip is a file anybody can hand you and an entry called `../../.bashrc` is
     * a real thing that happens.
     */
    fun readFrom(source: File) {
        if (busy) return
        busy = true
        outcome = null
        scope.launch {
            var restored = 0
            val done = runCatching {
                ZipInputStream(source.inputStream().buffered()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name in PARTS -> {
                                File(Store.folder, name).outputStream().use { zip.copyTo(it) }
                                restored += 1
                            }
                            name == SETTINGS -> {
                                applySettings(zip.readBytes().toString(Charsets.UTF_8))
                                restored += 1
                            }
                            // Anything else is either from a newer version or
                            // has no business being here. Both are ignored.
                            else -> Unit
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            busy = false
            outcome = done.fold(
                onSuccess = {
                    if (restored == 0) "Nothing in that file looked like a Blazify backup."
                    else "Restored $restored ${if (restored == 1) "part" else "parts"} — " +
                        "restart Blazify to see them."
                },
                onFailure = { "Couldn't read that file." },
            )
        }
    }

    // ── preferences, as text ─────────────────────────────────────────────────

    /**
     * The three places preferences live.
     *
     * Written as `branch/key=value` so a restore knows which node each belongs
     * to — one flat list would put the equaliser's settings in with the look's
     * and quietly lose whichever collided.
     */
    private val BRANCHES = listOf("", "look", "eq")

    private fun node(branch: String): Preferences =
        Preferences.userRoot().node(
            if (branch.isEmpty()) "com/blazify/desktop" else "com/blazify/desktop/$branch",
        )

    private fun settingsAsText(): String = BRANCHES.flatMap { branch ->
        runCatching {
            node(branch).keys().map { key -> "$branch/$key=${node(branch).get(key, "")}" }
        }.getOrDefault(emptyList())
    }.joinToString("\n")

    private fun applySettings(text: String) {
        text.lineSequence().forEach { line ->
            val at = line.indexOf('=')
            if (at <= 0) return@forEach
            val path = line.substring(0, at)
            val slash = path.indexOf('/')
            if (slash < 0) return@forEach
            val branch = path.substring(0, slash)
            val key = path.substring(slash + 1)
            if (branch !in BRANCHES || key.isBlank()) return@forEach
            // The account and its friends are never written out, so they can
            // never be written back — but a hand-edited file could try.
            if (key in PRIVATE) return@forEach
            runCatching { node(branch).put(key, line.substring(at + 1)) }
        }
        BRANCHES.forEach { runCatching { node(it).flush() } }
    }

    /** What the backup will contain, said before it is made. */
    fun summary(): String {
        val counts = buildList {
            Library.liked.size.takeIf { it > 0 }?.let { add("$it liked") }
            Playlists.all.size.takeIf { it > 0 }?.let { add("$it playlist${if (it == 1) "" else "s"}") }
            Library.history.size.takeIf { it > 0 }?.let { add("$it played") }
            Library.saved.size.takeIf { it > 0 }?.let { add("$it saved") }
        }
        return if (counts.isEmpty()) "Nothing to back up yet" else counts.joinToString(" · ")
    }
}
