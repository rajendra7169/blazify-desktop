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
fun main(): Unit = runBlocking {
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
        println("  found one, but it wouldn't be borrowed: ${Headless.trouble}")
        return@runBlocking
    }
    println("  borrowed ${Headless.borrowed} in ${took}ms")

    // Arithmetic and the engine's own name: enough to prove that script goes in
    // and a value comes back, which is the whole of what the rest depends on.
    println("  2 + 2 = ${Headless.evaluate("String(2 + 2)")}")
    println("  it says it is: ${Headless.evaluate("navigator.userAgent")}")
    println("  and it can fetch: ${Headless.evaluate("typeof fetch")}")

    val again = System.currentTimeMillis()
    Headless.evaluate("String(1)")
    println("  a second question took ${System.currentTimeMillis() - again}ms")

    Headless.stop()
    println("done")
}
