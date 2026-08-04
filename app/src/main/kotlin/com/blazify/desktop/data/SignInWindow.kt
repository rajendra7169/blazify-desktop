package com.blazify.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A browser window this app opened, on a profile only this app uses.
 *
 * Borrowing the session out of somebody's everyday browser turned out to be
 * borrowing something that doesn't hold still: the site rotates it every few
 * minutes, keeps the new values in the running browser's memory, and refuses
 * the ones left on disk. There is no arrangement of instructions that fixes
 * that, because the browser is doing exactly what it should.
 *
 * So the app asks for a window of its own. It is a real browser — the one
 * already on the machine — showing Google's real sign-in page, with no page
 * and no password anywhere near this application. What's different is the
 * profile: it belongs to the app, nothing else opens it, and so the session
 * written into it when the window closes is still the current one when it is
 * read a second later.
 */
object SignInWindow {

    /** Where the window this app opens keeps everything of its own. */
    val profile: File get() = File(Store.folder, "browser")

    private val onWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** A browser the app knows how to open a window of. */
    data class Opener(
        val label: String,
        val program: String,
        val kind: BrowserSession.Kind,
        /** What it files its cookie key under in the desktop's password store. */
        val keyring: String = "chromium",
    )

    /**
     * Which browser the window will be. The first one that's installed.
     *
     * Firefox and its relatives are preferred where they're present: they keep
     * cookies in a plain file, so nothing has to be unlocked at all, and a
     * separate profile is a first-class idea to them rather than a flag.
     */
    private val OPENERS = listOf(
        Opener("Firefox", "firefox", BrowserSession.Kind.Firefox),
        Opener("LibreWolf", "librewolf", BrowserSession.Kind.Firefox),
        Opener("Zen", "zen-browser", BrowserSession.Kind.Firefox),
        Opener("Waterfox", "waterfox", BrowserSession.Kind.Firefox),
        Opener("Floorp", "floorp", BrowserSession.Kind.Firefox),
        Opener("Brave", "brave-browser", BrowserSession.Kind.Chromium, "brave"),
        Opener("Brave", "brave", BrowserSession.Kind.Chromium, "brave"),
        Opener("Chrome", "google-chrome-stable", BrowserSession.Kind.Chromium, "chrome"),
        Opener("Chrome", "google-chrome", BrowserSession.Kind.Chromium, "chrome"),
        Opener("Chromium", "chromium", BrowserSession.Kind.Chromium, "chromium"),
        Opener("Chromium", "chromium-browser", BrowserSession.Kind.Chromium, "chromium"),
        Opener("Edge", "microsoft-edge", BrowserSession.Kind.Chromium, "chromium"),
        Opener("Vivaldi", "vivaldi", BrowserSession.Kind.Chromium, "vivaldi"),
        Opener("Opera", "opera", BrowserSession.Kind.Chromium, "opera"),
    )

    /** The same list, where Windows keeps its programs. */
    private val WINDOWS_OPENERS = listOf(
        Opener("Firefox", "C:\\Program Files\\Mozilla Firefox\\firefox.exe", BrowserSession.Kind.Firefox),
        Opener("Brave", "C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe", BrowserSession.Kind.Chromium),
        Opener("Chrome", "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", BrowserSession.Kind.Chromium),
        Opener("Chrome", "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe", BrowserSession.Kind.Chromium),
        Opener("Edge", "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe", BrowserSession.Kind.Chromium),
    )

    /** The browser a window would be opened in, or nothing if there isn't one. */
    fun opener(): Opener? =
        if (onWindows) WINDOWS_OPENERS.firstOrNull { File(it.program).isFile }
        else OPENERS.firstOrNull { which(it.program) != null }

    private fun which(program: String): String? = runCatching {
        val process = ProcessBuilder("which", program).redirectErrorStream(true).start()
        val path = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && path.isNotBlank()) path else null
    }.getOrNull()

    /**
     * Open the window, wait for it to be closed, and read what it holds.
     *
     * Waiting for the window to close is the whole point of doing it this way:
     * a browser writes its cookies out when it quits, and this one is quit by
     * the person who just signed in, on purpose, with nothing else running in
     * the profile to move them on afterwards.
     */
    suspend fun signIn(): Result<String> = withContext(Dispatchers.IO) {
        val opener = opener()
            ?: return@withContext Result.failure(
                IllegalStateException("No browser on this machine to open a window in"),
            )

        profile.mkdirs()
        val command = when (opener.kind) {
            BrowserSession.Kind.Firefox -> listOf(
                opener.program,
                "-profile", profile.absolutePath,
                // Its own instance, so an already-running copy doesn't swallow
                // the request and open a tab in somebody's everyday window.
                "-no-remote",
                "-new-instance",
                SITE,
            )
            BrowserSession.Kind.Chromium -> listOf(
                opener.program,
                "--user-data-dir=${profile.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
                // A window, not a tab in something else, and not the whole
                // apparatus of a browser people are meant to live in.
                "--new-window",
                SITE,
            )
        }

        val process = runCatching { ProcessBuilder(command).start() }
            .getOrElse { return@withContext Result.failure(it) }
        process.waitFor()

        // Where that browser will have put the cookies, once it has quit.
        val store = when (opener.kind) {
            BrowserSession.Kind.Firefox ->
                profile.walkTopDown().maxDepth(2).firstOrNull { it.name == "cookies.sqlite" }
            BrowserSession.Kind.Chromium -> listOf(
                File(profile, "Default/Network/Cookies"),
                File(profile, "Default/Cookies"),
            ).firstOrNull { it.isFile }
        } ?: return@withContext Result.failure(
            IllegalStateException("${opener.label} closed without signing in"),
        )

        BrowserSession.sessionFrom(
            BrowserSession.Browser(opener.label, store, opener.kind, opener.keyring),
        )
    }

    /**
     * Throw the window's profile away.
     *
     * It holds a signed-in Google session, which is the account itself. Signing
     * out of the app and leaving that on disk would make signing out a gesture
     * rather than a thing that happened.
     */
    fun forget() {
        runCatching { profile.deleteRecursively() }
    }

    private const val SITE = "https://music.youtube.com/"
}
