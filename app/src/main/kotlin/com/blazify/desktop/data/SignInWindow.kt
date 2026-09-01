package com.blazify.desktop.data

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
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
     *
     * The order is a preference and not a decision. A browser that refuses to
     * open a window of its own hands the turn to the next one — see below,
     * where that refusal is caught.
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

    /**
     * The browsers Windows might have, told apart by the name of the program.
     *
     * The order is the same preference as the list above and doubles as it:
     * what the registry hands back arrives in whatever order the registry
     * felt like, and is sorted back into this one.
     */
    private data class Known(
        val label: String,
        val kind: BrowserSession.Kind,
        val keyring: String = "chromium",
    )

    private val WINDOWS_KNOWN = linkedMapOf(
        "firefox.exe" to Known("Firefox", BrowserSession.Kind.Firefox),
        "librewolf.exe" to Known("LibreWolf", BrowserSession.Kind.Firefox),
        "zen.exe" to Known("Zen", BrowserSession.Kind.Firefox),
        "waterfox.exe" to Known("Waterfox", BrowserSession.Kind.Firefox),
        "floorp.exe" to Known("Floorp", BrowserSession.Kind.Firefox),
        "brave.exe" to Known("Brave", BrowserSession.Kind.Chromium, "brave"),
        "chrome.exe" to Known("Chrome", BrowserSession.Kind.Chromium, "chrome"),
        "chromium.exe" to Known("Chromium", BrowserSession.Kind.Chromium),
        "msedge.exe" to Known("Edge", BrowserSession.Kind.Chromium),
        "vivaldi.exe" to Known("Vivaldi", BrowserSession.Kind.Chromium, "vivaldi"),
        "opera.exe" to Known("Opera", BrowserSession.Kind.Chromium, "opera"),
    )

    /**
     * Where Windows keeps its programs, when nothing has said otherwise.
     *
     * A floor rather than the answer — the registry below is what actually
     * finds a browser, and this catches one that installed without registering
     * itself properly. Program Files is where an administrator's install
     * lands; AppData is where Chrome and Brave land by default, and that is
     * the half that used to be missing.
     */
    private val windowsGuesses: List<String>
        get() {
            val local = System.getenv("LOCALAPPDATA")
                ?: "${System.getProperty("user.home")}\\AppData\\Local"
            val files = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val filesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            return listOf(
                "$files\\Mozilla Firefox\\firefox.exe",
                "$filesX86\\Mozilla Firefox\\firefox.exe",
                "$files\\LibreWolf\\librewolf.exe",
                "$files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "$filesX86\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "$local\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "$files\\Google\\Chrome\\Application\\chrome.exe",
                "$filesX86\\Google\\Chrome\\Application\\chrome.exe",
                "$local\\Google\\Chrome\\Application\\chrome.exe",
                "$local\\Chromium\\Application\\chrome.exe",
                "$files\\Microsoft\\Edge\\Application\\msedge.exe",
                "$filesX86\\Microsoft\\Edge\\Application\\msedge.exe",
                "$files\\Vivaldi\\Application\\vivaldi.exe",
                "$local\\Vivaldi\\Application\\vivaldi.exe",
                "$local\\Programs\\Opera\\opera.exe",
            )
        }

    /**
     * Which browsers Windows has, asked of Windows rather than guessed at.
     *
     * This was five paths under Program Files, which is where an install made
     * by an administrator goes. Chrome and Brave install per-user by default,
     * under AppData, where nothing was looking — so somebody with Chrome and
     * nothing else was told there was no browser on this machine to open a
     * window in while looking straight at one. That is the sign-in failing
     * before it has begun, and it is the ordinary case rather than a rare one.
     *
     * A browser registers itself under StartMenuInternet when it installs,
     * wherever it put itself: per-user installs in HKCU, machine-wide ones in
     * HKLM, and 32-bit ones in the WOW6432Node view of the same. Reading all
     * three puts the question to the only thing that knows the answer.
     */
    private fun windowsOpeners(): List<Opener> {
        val paths = LinkedHashSet<String>()
        paths += registered()
        paths += windowsGuesses

        val found = LinkedHashMap<String, Opener>()
        for (path in paths) {
            val file = File(path)
            if (!file.isFile) continue
            val known = WINDOWS_KNOWN[file.name.lowercase()] ?: continue
            // The same program reached by two routes is still one browser.
            found.putIfAbsent(
                file.absolutePath.lowercase(),
                Opener(known.label, file.absolutePath, known.kind, known.keyring),
            )
        }

        val order = WINDOWS_KNOWN.keys.toList()
        return found.values.sortedBy { order.indexOf(File(it.program).name.lowercase()) }
    }

    /** Every program Windows has been told is a browser. */
    private fun registered(): List<String> = buildList {
        val hives = listOf(
            WinReg.HKEY_CURRENT_USER to "SOFTWARE\\Clients\\StartMenuInternet",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\Clients\\StartMenuInternet",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\WOW6432Node\\Clients\\StartMenuInternet",
        )
        for ((hive, path) in hives) {
            val keys = runCatching { Advapi32Util.registryGetKeys(hive, path) }.getOrNull() ?: continue
            for (key in keys) {
                runCatching {
                    Advapi32Util.registryGetStringValue(hive, "$path\\$key\\shell\\open\\command", null)
                }.getOrNull()?.let { command -> programIn(command)?.let(::add) }
            }
        }

        // And the older register, for anything that listed its program without
        // announcing itself as a browser.
        val appPaths = listOf(
            WinReg.HKEY_CURRENT_USER to "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths",
        )
        for ((hive, path) in appPaths) {
            for (program in WINDOWS_KNOWN.keys) {
                runCatching {
                    Advapi32Util.registryGetStringValue(hive, "$path\\$program", null)
                }.getOrNull()?.let { command -> programIn(command)?.let(::add) }
            }
        }
    }

    /**
     * The program out of a registered command.
     *
     * What is kept there is a command line rather than a path — quoted when it
     * has a space in it, and sometimes with arguments following.
     */
    private fun programIn(command: String): String? {
        val text = command.trim()
        if (text.startsWith("\"")) return text.drop(1).substringBefore('"').ifBlank { null }
        val ends = text.indexOf(".exe", ignoreCase = true)
        return if (ends < 0) null else text.take(ends + 4).ifBlank { null }
    }

    /** Every browser on this machine a window could be opened in. */
    fun openers(): List<Opener> =
        if (onWindows) windowsOpeners()
        else OPENERS.filter { which(it.program) != null }

    /** The one that would be tried first. */
    fun opener(): Opener? = openers().firstOrNull()

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
    enum class Stage { Opened, SignedIn, Collecting }

    /**
     * Whether a window is already open for this.
     *
     * One at a time, always. Everything below launches a browser, and a second
     * call while the first is still waiting is a second window — which is how
     * this managed to open windows until a machine fell over.
     */
    @Volatile
    private var busy = false

    /**
     * Sign in, trying each browser on the machine at most once.
     *
     * Flat rather than recursive, and that is the whole point of the rewrite.
     * The first version called itself when a browser refused, passing the rest
     * of the list minus the one that had just failed — so Firefox failing tried
     * Brave, which failing tried Firefox again, which tried Brave again. Every
     * branch opened a window. On a machine where launchers hand off to a
     * running copy and exit immediately, that is every branch, and the count
     * doubles at each step until the machine gives up. It did.
     */
    suspend fun signIn(
        verify: suspend (String) -> Boolean = { true },
        onStage: (Stage) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        if (busy) {
            return@withContext Result.failure(
                IllegalStateException("A sign-in window is already open — finish or close it first"),
            )
        }
        busy = true
        try {
            val available = openers()
            if (available.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No browser on this machine to open a window in"),
                )
            }

            var refused: String? = null
            for (opener in available) {
                when (val answer = attempt(opener, verify, onStage)) {
                    is Attempt.Signed -> return@withContext Result.success(answer.session)
                    // It opened and nothing came of it. Trying the next browser
                    // would open a second window over somebody who has just
                    // decided not to sign in, which is worse than saying so.
                    is Attempt.Nothing -> return@withContext Result.failure(
                        IllegalStateException(answer.why),
                    )
                    // It never opened at all. This is the only case worth
                    // handing on, and each browser gets exactly one turn.
                    is Attempt.Refused -> refused = refused ?: answer.why
                }
            }
            Result.failure(IllegalStateException(refused ?: "No browser would open a window"))
        } finally {
            busy = false
        }
    }

    /** What one browser had to say for itself. */
    private sealed interface Attempt {
        data class Signed(val session: String) : Attempt
        data class Nothing(val why: String) : Attempt
        data class Refused(val why: String) : Attempt
    }

    private suspend fun attempt(
        opener: Opener,
        verify: suspend (String) -> Boolean,
        onStage: (Stage) -> Unit,
    ): Attempt {
        profile.mkdirs()
        // Which pages the last window was on is how this one knows it has
        // arrived, and last time it arrived — so leaving that behind means
        // arriving before the window has even opened.
        runCatching { File(profile, "Default/Sessions").deleteRecursively() }
        runCatching { File(profile, "sessionstore-backups").deleteRecursively() }

        val command = when (opener.kind) {
            BrowserSession.Kind.Firefox -> listOf(
                opener.program,
                "-profile", profile.absolutePath,
                "-no-remote",
                "-new-instance",
                SITE,
            )
            // No debugging port here, deliberately. The door this app asks the
            // session through is the same door Google watches for: a browser
            // that opens it is refused the sign-in outright, with "this browser
            // or app may not be secure" and no way past it. The door is opened
            // afterwards instead — see [collect].
            BrowserSession.Kind.Chromium -> listOf(
                opener.program,
                "--user-data-dir=${profile.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
                "--new-window",
                SITE,
            )
        }

        val opened = System.currentTimeMillis()
        val process = runCatching {
            ProcessBuilder(command).apply {
                // Firefox refuses to start a second copy of itself while one is
                // running, whatever the flags say — the switch that asks for a
                // separate instance is advice, and this is the instruction.
                if (opener.kind == BrowserSession.Kind.Firefox) {
                    environment()["MOZ_NO_REMOTE"] = "1"
                }
            }.start()
        }.getOrElse { return Attempt.Refused("${opener.label} wouldn't start") }

        onStage(Stage.Opened)

        // A launcher that hands its work to an already-running copy and exits
        // is not a window somebody is looking at. Given a moment to prove
        // otherwise: a window that is genuinely open is still open two seconds
        // later, and one that handed off is not.
        kotlinx.coroutines.delay(2500)
        if (!process.isAlive && !anythingOpen()) {
            return Attempt.Refused("${opener.label} wouldn't open a window of its own")
        }

        val giveUpAt = System.currentTimeMillis() + 15 * 60 * 1000
        var caught: String? = null
        var arrived = 0L

        while (alive(process, opener) && System.currentTimeMillis() < giveUpAt) {
            kotlinx.coroutines.delay(1000)

            // Firefox keeps its cookies in a plain file, so there is something
            // to read while the window stands open. Chromium's are sealed until
            // the browser itself is asked, and asking has to wait until the
            // sign-in is over — so for those there is nothing to do here but
            // watch for the landing.
            if (opener.kind == BrowserSession.Kind.Firefox) {
                val held = read(opener)?.getOrNull()?.takeIf { "SAPISID" in it }
                if (held != null && verify(held)) {
                    caught = held
                    break
                }
            }

            if (arrived == 0L && landed(opened)) {
                arrived = System.currentTimeMillis()
                onStage(Stage.SignedIn)
            }

            // Landed on the music site, which is where the sign-in page sends
            // somebody it has let through. Firefox still has to write its file
            // out on a timer of its own; for Chromium the window has done its
            // job and closing it is what unlocks the profile to be read.
            val patience = if (opener.kind == BrowserSession.Kind.Chromium) SETTLE else PATIENCE

            if (arrived != 0L && System.currentTimeMillis() - arrived > patience) {
                if (opener.kind == BrowserSession.Kind.Firefox) {
                    close(process)
                    caught = read(opener)?.getOrNull()?.takeIf { "SAPISID" in it }
                        ?: run {
                            kotlinx.coroutines.delay(1500)
                            read(opener)?.getOrNull()?.takeIf { "SAPISID" in it }
                        }
                    if (caught?.let { verify(it) } != true) caught = null
                }
                break
            }
        }

        close(process)
        caught?.let { return Attempt.Signed(it) }

        // The window is shut, either because it had plainly finished or because
        // somebody shut it themselves. Both mean the same thing here: whatever
        // was signed in to is now sitting in the profile, and this is the point
        // at which it can be asked for.
        if (opener.kind == BrowserSession.Kind.Chromium) {
            onStage(Stage.Collecting)
            collect(opener, verify)?.let { return Attempt.Signed(it) }
        }

        // Closed by hand, perhaps after signing in — a browser writes its
        // cookies out as it quits, so that is worth one more look.
        val last = read(opener)?.getOrNull()?.takeIf { "SAPISID" in it }
        if (last != null && verify(last)) return Attempt.Signed(last)

        // What the browser said when it was asked, rather than a guess. On
        // Windows this is where "Chrome keeps its cookies in a form only it can
        // read" comes from — the difference between somebody being told what
        // happened and somebody watching a sign-in they completed do nothing.
        val why = read(opener)?.exceptionOrNull()?.message

        return Attempt.Nothing(
            when {
                why != null && arrived != 0L -> "You signed in, but $why"
                arrived != 0L ->
                    "You signed in, but ${opener.label} didn't hand the session over. " +
                        "Paste it by hand instead — the button below does it in one step."
                else -> "The ${opener.label} window was closed before signing in"
            },
        )
    }

    /**
     * Ask the browser for the session, once nobody is signing in any more.
     *
     * This used to happen through the same window somebody was typing their
     * password into, and that is what broke it: a Chromium browser started with
     * its tooling port open is a browser Google will not accept a sign-in
     * through. It says "this browser or app may not be secure" and there is no
     * arrangement of the other flags that talks it round — the objection is to
     * the port, and the port is the whole point of it.
     *
     * The two jobs do not have to happen at once, though. Signing in needs an
     * ordinary browser and nothing else; reading the session needs the port and
     * nothing else. So the window somebody uses has no port, and when it closes
     * the same profile is opened a second time — headless, for a second or two,
     * with the port open and nobody signing in through it. The cookies are
     * already there. Nothing is decrypted, and Google never sees the door.
     */
    private suspend fun collect(
        opener: Opener,
        verify: suspend (String) -> Boolean,
    ): String? {
        // A second copy on a profile the first is still holding would hand its
        // work over to the first and exit — which is a browser with no port
        // again, and no way to ask it anything. So wait for the first to let
        // go, up to ten seconds, before opening the second.
        val letGoBy = System.currentTimeMillis() + 10_000
        while (anythingOpen() && System.currentTimeMillis() < letGoBy) {
            kotlinx.coroutines.delay(500)
        }

        val process = runCatching {
            ProcessBuilder(
                opener.program,
                "--user-data-dir=${profile.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
                // Nobody is looking at this one, and a window appearing after
                // the sign-in window has closed would be the app apparently
                // starting over.
                "--headless=new",
                "--remote-debugging-port=0",
                LANDING,
            ).redirectErrorStream(true).start()
        }.getOrElse { return null }

        try {
            val giveUpAt = System.currentTimeMillis() + 45_000
            while (System.currentTimeMillis() < giveUpAt) {
                kotlinx.coroutines.delay(1000)
                val held = BrowserTalk.session(profile) ?: continue
                if (verify(held)) return held
            }
            return null
        } finally {
            // Nothing was signed in to here and nothing needs writing down, so
            // this one can simply be stopped.
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }


    /**
     * Whether that browser is still up, however it was started.
     *
     * The process that was launched is not always the browser: on some systems
     * a launcher starts the real thing and exits, and on Windows a second copy
     * hands its work to the first and exits immediately. Watching only the
     * process that was started would call a perfectly good window dead.
     */
    private fun alive(process: Process, opener: Opener): Boolean =
        process.isAlive || anythingOpen()

    /**
     * Whether a browser pointed at *this* profile is running.
     *
     * The question has to be about the profile and not about the program.
     * Windows was asked whether anything called msedge.exe was running, and on
     * a machine where somebody has their own browser open the answer is always
     * yes — a dozen times over, since a browser is a dozen processes. So the
     * window this app opened was reported as still standing long after it had
     * been closed, and a sign-in somebody had thought better of sat there
     * until the fifteen-minute timeout gave up on it. Nothing had gone wrong
     * that a person could see; the app had simply stopped watching them and
     * started watching their browser.
     *
     * The profile is this application's own and appears on the command line of
     * the window it opened, and nowhere else on the machine. Asking for
     * command lines rather than names asks the right question, and is the same
     * question `pgrep -f` has always answered on Linux.
     *
     * Only reached when the process that was launched has already exited —
     * [alive] asks that first — so listing processes is rare rather than
     * once a second.
     */
    private fun anythingOpen(): Boolean = runCatching {
        val marker = profile.absolutePath
        val command = if (onWindows) {
            listOf(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                // Every process rather than a filtered few, and the matching
                // done here instead. A filter would need quotes inside this
                // argument, and Windows does its own quoting as it hands the
                // argument over — the inner ones do not survive the trip.
                "Get-CimInstance Win32_Process | ForEach-Object { \$_.CommandLine }",
            )
        } else {
            listOf("pgrep", "-f", marker)
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        if (onWindows) output.contains(marker, ignoreCase = true) else output.isNotBlank()
    }.getOrDefault(false)


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
     * How long to leave a Chromium window standing once it has arrived.
     *
     * Nothing is being waited for here — the session is asked of the browser
     * afterwards rather than read off the disk, so there is no commit to
     * outlast. This is only long enough that the window does not vanish out
     * from under somebody the instant the page loads, which reads as a crash
     * rather than as success.
     */
    private const val SETTLE = 4_000L

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
        if (onWindows) {
            // Windows has no polite way to stop a process from Java: destroy
            // and destroyForcibly are the same call, and it is the abrupt one.
            // Using it here would kill the browser mid-sentence, before it had
            // written the session anywhere — and the session on disk is the
            // whole of what the next step reads. Asking its windows to close
            // is the request that destroy() only looks like.
            runCatching {
                ProcessBuilder("taskkill", "/PID", process.pid().toString(), "/T")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            }
        } else {
            process.children().forEach { it.destroy() }
            process.destroy()
        }

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
