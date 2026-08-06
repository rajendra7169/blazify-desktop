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
 * Whether a browser will hand its session over when asked.
 *
 * Launched the way the application launches it, asked the way the application
 * asks. Prints names only — a session is the account itself and has no
 * business in a terminal.
 */
fun main(): Unit = runBlocking {
    val opener = SignInWindow.openers().firstOrNull { it.kind.name == "Chromium" }
    if (opener == null) {
        println("no browser here that answers this way")
        return@runBlocking
    }
    println("asking ${opener.label}")

    val profile = File(System.getProperty("java.io.tmpdir"), "blazify-talk-probe")
    profile.deleteRecursively()
    profile.mkdirs()

    val process = ProcessBuilder(
        opener.program,
        "--user-data-dir=${profile.absolutePath}",
        "--no-first-run",
        "--no-default-browser-check",
        "--headless=new",
        "--remote-debugging-port=0",
        "https://www.youtube.com/",
    ).redirectErrorStream(true).start()

    var answered = false
    repeat(20) {
        delay(1000)
        val session = BrowserTalk.session(profile)
        if (session != null) {
            println("  signed-in session handed over: ${session.split("; ").size} cookies")
            answered = true
            return@repeat
        }
    }
    if (!answered) {
        // Signed out, which is the expected answer here: nobody has logged in
        // to this throwaway profile. What matters is that the door answered at
        // all rather than the browser being asked to decrypt anything.
        println("  no signed-in session (expected — nothing has signed in to this profile)")
        println("  door answered: ${File(profile, "DevToolsActivePort").exists()}")
    }
    process.descendants().forEach { it.destroy() }
    process.destroy()
    profile.deleteRecursively()
}
