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
     * Open the window, watch for the sign-in landing, and close it again.
     *
     * The window is the app's, so putting it away is the app's job — asking
     * somebody to close it themselves is asking them to finish a task the
     * program could see was already done.
     *
     * Watched rather than waited on: the profile is checked every couple of
     * seconds, and the moment it holds a session the catalogue actually
     * accepts, the window is closed and that session is the answer. Closing it
     * by hand still works and lands in the same place — it just isn't
     * necessary any more.
     */
    /** What the window is doing, for saying so on screen while it does it. */
    enum class Stage { Opened, SignedIn }

    suspend fun signIn(
        verify: suspend (String) -> Boolean = { true },
        onStage: (Stage) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        val opener = opener()
            ?: return@withContext Result.failure(
                IllegalStateException("No browser on this machine to open a window in"),
            )

        profile.mkdirs()
        // Which pages the last window was on is how this one knows it has
        // arrived, and last time it arrived — so leaving that behind means
        // arriving before the window has even opened. It is tab state and
        // nothing else; throwing it away costs nothing and is the difference
        // between watching for a landing and remembering an old one.
        runCatching { File(profile, "Default/Sessions").deleteRecursively() }
        runCatching { File(profile, "sessionstore-backups").deleteRecursively() }
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

        val opened = System.currentTimeMillis()
        val process = runCatching { ProcessBuilder(command).start() }
            .getOrElse { return@withContext Result.failure(it) }

        onStage(Stage.Opened)

        // Long enough for a password, a second factor and a change of mind.
        val giveUpAt = System.currentTimeMillis() + 15 * 60 * 1000
        var caught: String? = null
        var arrived = 0L

        while (process.isAlive && System.currentTimeMillis() < giveUpAt) {
            kotlinx.coroutines.delay(1000)

            // The session on disk, which is the only thing that settles this.
            //
            // Measured the hard way: a browser keeps its newest cookies in
            // memory and commits them on a timer, and being killed is not a
            // reliable way to make it write. Closing the window the moment the
            // sign-in landed produced a profile holding a completed sign-in and
            // no session at all — right up to the last redirect and nothing to
            // show for it. So the window stays open until the thing being
            // waited for actually exists.
            read(opener)?.getOrNull()?.takeIf { "SAPISID" in it }?.let { session ->
                if (verify(session)) {
                    caught = session
                    close(process)
                    return@let
                }
            }
            if (caught != null) break

            // Then the arrival, for the ones that don't.
            //
            // Measured rather than assumed: eight seconds into browsing, a
            // Chromium cookie store held nothing at all — every cookie was
            // still in memory, and they all appeared the instant the browser
            // was asked to quit. Waiting for them to show up on their own means
            // waiting out a thirty-second timer for something the window could
            // have said in two.
            //
            // What it does say quickly is where it has been. Landing on the
            // music site only happens after the sign-in page lets you through,
            // so that is the signal: close the window, which writes the cookies
            // out, and read them a moment later.
            // Landing says the sign-in went through, which is worth saying on
            // screen — somebody watching a window that has plainly finished sit
            // there for another half-minute deserves to know it is waiting on
            // the browser rather than on them.
            if (arrived == 0L && landed(opened)) {
                arrived = System.currentTimeMillis()
                onStage(Stage.SignedIn)
            }

            // It has arrived and the session still is not on disk. The browser
            // is asked to go anyway, and what it leaves behind is taken on its
            // merits — better than a window that never closes.
            if (arrived != 0L && System.currentTimeMillis() - arrived > PATIENCE) {
                close(process)
                process.waitFor()
                caught = read(opener)?.getOrNull()?.takeIf { "SAPISID" in it }
                    ?: run {
                        kotlinx.coroutines.delay(1500)
                        read(opener)?.getOrNull()?.takeIf { "SAPISID" in it }
                    }
                if (caught?.let { verify(it) } != true) caught = null
                break
            }
        }

        process.waitFor()
        // Either it was closed here, or somebody closed it themselves — and a
        // browser writes its cookies out as it quits, so the second case is
        // worth one more look rather than a shrug.
        caught
            ?: return@withContext read(opener)
                ?: Result.failure(IllegalStateException("${opener.label} closed without signing in"))

        Result.success(caught)
    }

    /**
     * Whether the window has reached the music site.
     *
     * A browser writes down which pages its tabs are on within a second or two
     * of arriving — long before it writes down anything else — and the address
     * it started on can't be mistaken for this one, because there the music
     * site appears only as an escaped parameter and never as a place that has
     * been visited.
     */
    private fun landed(since: Long): Boolean =
        File(profile, "Default/Sessions").listFiles().orEmpty().any { file ->
            runCatching {
                // Written by this window, not a previous one. Belt as well as
                // braces: the folder is cleared before the window opens, and a
                // file that predates the opening is still not evidence about it.
                if (file.lastModified() < since) return@runCatching false
                // Read as bytes-as-characters: it's a binary record and the only
                // thing being looked for in it is a plain run of ASCII.
                file.readText(Charsets.ISO_8859_1).contains(LANDING)
            }.getOrDefault(false)
        }

    /** The cookies that window has written down so far, if it has written any. */
    private suspend fun read(opener: Opener): Result<String>? {
        val store = when (opener.kind) {
            BrowserSession.Kind.Firefox ->
                profile.walkTopDown().maxDepth(2).firstOrNull { it.name == "cookies.sqlite" }
            BrowserSession.Kind.Chromium -> listOf(
                File(profile, "Default/Network/Cookies"),
                File(profile, "Default/Cookies"),
            ).firstOrNull { it.isFile }
        } ?: return null

        return BrowserSession.sessionFrom(
            BrowserSession.Browser(opener.label, store, opener.kind, opener.keyring),
        )
    }

    /**
     * How long to wait, after the sign-in has plainly worked, for the browser
     * to write it down.
     *
     * It commits on a timer of its own — half a minute, give or take — and
     * this has to outlast that, or the window closes on the wrong side of the
     * one write that matters.
     */
    private const val PATIENCE = 50_000L

    /**
     * Put the window away, and let it tidy up on the way.
     *
     * The browser's own process is asked to stop — not every process it owns.
     * A browser writes its cookies out as it shuts down, and shutting down is
     * something only the one in charge can do: killing the workers first takes
     * away the hands it would have written with, which is exactly how a
     * completed sign-in ended up leaving nothing behind.
     *
     * The script it was launched through is asked too, since that waits on the
     * real program rather than becoming it, and ending only what was started
     * would leave the window standing there.
     */
    private fun close(process: Process) {
        process.children().forEach { it.destroy() }
        process.destroy()

        // Only if it will not go quietly. Force is the thing that loses the
        // cookies, so it is a last resort with a long fuse rather than a
        // second attempt.
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
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

    /**
     * Google's sign-in page, asked for by name.
     *
     * Opening the music site instead means landing on a home page and having to
     * find the sign-in button on it — a step in a flow whose whole purpose is
     * signing in. This is the same page that button leads to, asked for
     * directly, and the continue address brings the window back to the music
     * site afterwards, which is where the session has to be set for it to be
     * worth anything.
     */
    /** The address that means the sign-in page has let somebody through. */
    private const val LANDING = "https://music.youtube.com"

    private const val SITE =
        "https://accounts.google.com/ServiceLogin" +
            "?ltmpl=music&service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"
}
