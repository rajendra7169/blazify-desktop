package com.blazify.desktop.tools.session

import com.blazify.desktop.data.BrowserTalk
import com.blazify.desktop.data.SignInWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The second half of signing in, run against a profile somebody has signed in
 * to already.
 *
 * The window that takes the password cannot have the tooling port open — Google
 * refuses a sign-in through one that does. So the port is opened afterwards, on
 * a profile that is no longer being typed into, and this is that step on its
 * own: shut whatever is holding the profile, open it again headless with the
 * port, and ask.
 *
 * Prints a count and a yes or no. The session is the account itself and has no
 * business in a terminal.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val profile = File(args.firstOrNull() ?: SignInWindow.profile.absolutePath)
    println("profile: ${profile.absolutePath}")
    if (!profile.isDirectory) {
        println("  no such profile — sign in first")
        return@runBlocking
    }

    val opener = SignInWindow.openers().firstOrNull { it.kind == com.blazify.desktop.data.BrowserSession.Kind.Chromium }
    if (opener == null) {
        println("  no Chromium browser here")
        return@runBlocking
    }
    println("browser: ${opener.label}")

    // Step one, the way the fix does it: ask the windows to close rather than
    // terminating the process, so the browser writes its cookies out first.
    val holding = holders(profile)
    println("holding the profile: $holding process(es)")
    holders(profile, close = true)
    val letGoBy = System.currentTimeMillis() + 20_000
    while (holders(profile) > 0 && System.currentTimeMillis() < letGoBy) delay(500)
    println("still holding after the ask: ${holders(profile)}")

    // Step two: the same profile, headless, with the port open.
    val process = ProcessBuilder(
        opener.program,
        "--user-data-dir=${profile.absolutePath}",
        "--no-first-run",
        "--no-default-browser-check",
        "--headless=new",
        "--remote-debugging-port=0",
        "https://music.youtube.com",
    ).redirectErrorStream(true).start()

    var answer: String? = null
    val giveUpAt = System.currentTimeMillis() + 45_000
    while (answer == null && System.currentTimeMillis() < giveUpAt) {
        delay(1000)
        answer = BrowserTalk.session(profile)
    }

    if (answer == null) {
        println()
        println("NO SESSION — the browser handed nothing over")
    } else {
        println()
        println("SESSION COLLECTED: ${answer.split("; ").size} cookies, SAPISID present")
    }

    process.descendants().forEach { it.destroyForcibly() }
    process.destroyForcibly()
}

/** How many processes hold this profile — and, if asked, request they close. */
private fun holders(profile: File, close: Boolean = false): Int = runCatching {
    val listing = ProcessBuilder(
        "powershell", "-NoProfile", "-NonInteractive", "-Command",
        "Get-CimInstance Win32_Process | ForEach-Object { \"\$(\$_.ProcessId) \$(\$_.CommandLine)\" }",
    ).redirectErrorStream(true).start()
    val output = listing.inputStream.bufferedReader().readText()
    listing.waitFor()

    val mine = output.lines().filter { it.contains(profile.absolutePath, ignoreCase = true) }
    if (close) {
        mine.mapNotNull { it.trim().substringBefore(' ').toIntOrNull() }.forEach { pid ->
            runCatching {
                ProcessBuilder("taskkill", "/PID", pid.toString(), "/T")
                    .redirectErrorStream(true).start().waitFor()
            }
        }
    }
    mine.size
}.getOrDefault(0)
