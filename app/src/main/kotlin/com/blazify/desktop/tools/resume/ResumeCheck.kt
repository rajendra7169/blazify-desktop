package com.blazify.desktop.tools.resume

import com.blazify.desktop.data.Resume
import com.blazify.desktop.data.Store
import com.blazify.desktop.data.Track

fun main() {
    val song = Track(id = "short", title = "A song", artist = "Someone", thumbnail = null, durationSeconds = null)
    val show = Track(id = "long", title = "An episode", artist = "A programme", thumbnail = null, durationSeconds = null)

    Resume.note(song, seconds = 90.0, length = 200.0)
    println("a three-minute song, halfway: ${Resume.mark("short")?.let { "remembered" } ?: "not remembered"}")

    Resume.note(show, seconds = 15.0, length = 3600.0)
    println("an hour, fifteen seconds in: ${Resume.mark("long")?.let { "remembered" } ?: "not remembered"}")

    Resume.note(show, seconds = 1200.0, length = 3600.0)
    println("an hour, twenty minutes in: ${Resume.mark("long")?.let { "remembered, ${it.left}" } ?: "not remembered"}")

    Resume.note(show, seconds = 3570.0, length = 3600.0)
    println("the same, at the very end: ${Resume.mark("long")?.let { "remembered" } ?: "dropped"}")

    Resume.note(show, seconds = 1800.0, length = 3600.0)
    println("on disk: ${Store.folder.resolve("resume.json").readText().take(80)}…")
    Resume.forgetAll()
}
