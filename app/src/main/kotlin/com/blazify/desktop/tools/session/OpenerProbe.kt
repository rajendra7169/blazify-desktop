package com.blazify.desktop.tools.session

import com.blazify.desktop.data.SignInWindow

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Which browsers this machine offers a sign-in window in.
 *
 * The sign-in fails before it starts when this list is empty, and it was empty
 * on any machine whose browser had installed itself per-user. Worth being able
 * to ask without opening a window.
 */
fun main() {
    val openers = SignInWindow.openers()
    if (openers.isEmpty()) {
        println("no browser found — the sign-in window has nothing to open")
        return
    }
    println("${openers.size} browser(s), in the order they would be tried:")
    openers.forEach { println("  ${it.label.padEnd(10)} ${it.kind}  ${it.program}") }
}
