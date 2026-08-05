package com.blazify.desktop.tools.panel

import com.blazify.desktop.data.Panel

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Whether the desktop can be answered, and what goes wrong when it cannot. */
fun main() {
    println("session bus: ${System.getenv("DBUS_SESSION_BUS_ADDRESS")}")
    Panel.start()
    println("took the name: ${Panel.answering}")
    Panel.trouble?.let { println("what went wrong: $it") }
    Thread.sleep(4000)
    println("still there: ${Panel.answering}")
}
