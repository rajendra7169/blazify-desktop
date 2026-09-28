package com.blazify.desktop.tools.engine

import com.blazify.desktop.data.Headless
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether this machine will lend its browser engine, and how quickly.
 *
 * Everything about playing a song now runs through that loan, so when nothing
 * plays the first question is whether the loan itself worked. Answering it from
 * a terminal takes a second; answering it by launching the window and pressing
 * play tells you only that something, somewhere, didn't.
 */
fun main(args: Array<String>): Unit = runBlocking {
    // Given a program, only that one is tried — which is how a particular
    // browser gets tested on a machine that has several, including one that
    // would otherwise never be reached because the first candidate works.
    //   ./gradlew :app:engineProbe --args="flatpak run org.mozilla.firefox"
    val only = args.joinToString(" ").takeIf { it.isNotBlank() }
    if (only != null) {
        println("trying only: $only")
        Headless.choose(only)
    }

    println("looking for a browser to borrow…")
    Headless.candidatesForProbe().forEachIndexed { at, (label, command) ->
        println("  ${at + 1}. $label — ${command.joinToString(" ")}")
    }
    if (!Headless.possible()) {
        println("  none found — ${Headless.trouble ?: "nothing Chromium-based on this machine"}")
        return@runBlocking
    }

    val startedAt = System.currentTimeMillis()
    val ok = Headless.ready()
    val took = System.currentTimeMillis() - startedAt
    if (!ok) {
        Headless.refusals.forEach { println("  refused: $it") }
        println("  none of them would be borrowed: ${Headless.trouble}")
        return@runBlocking
    }
    Headless.refusals.forEach { println("  refused: $it") }
    println("  borrowed ${Headless.borrowed} in ${took}ms")

    // Arithmetic and the engine's own name: enough to prove that script goes in
    // and a value comes back, which is the whole of what the rest depends on.
    println("  2 + 2 = ${Headless.evaluate("String(2 + 2)")}")
    Headless.lastComplaint?.let { println("  complaint: $it") }
    println("  it says it is: ${Headless.evaluate("navigator.userAgent")}")
    println("  and it can fetch: ${Headless.evaluate("typeof fetch")}")

    // Navigating is the other half of what the rest depends on, and the page it
    // has to reach is the service's own. This is the small text file there, not
    // the player script — enough to prove the address was reached and the
    // document belongs to that origin.
    val wentAt = System.currentTimeMillis()
    val went = Headless.open("https://www.youtube.com/robots.txt")
    println("  navigated: $went in ${System.currentTimeMillis() - wentAt}ms")
    println("  it is on: ${Headless.evaluate("location.host")}")

    val again = System.currentTimeMillis()
    Headless.evaluate("String(1)")
    println("  a second question took ${System.currentTimeMillis() - again}ms")

    Headless.stop()
    if (only != null) Headless.choose(null)
    println("done")
}
