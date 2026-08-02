package com.blazify.desktop.tools.lyrics

import com.blazify.desktop.data.LyricsProviders
import com.blazify.desktop.data.Track
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Ask every lyrics source for one song and print what each says.
 *
 *   ./gradlew :app:lyricsProbe --args="Kesariya|Arijit Singh|268"
 *
 * The sources are the part of this app most likely to break without anything
 * here changing — a service moves, a key rotates, a response gains a field.
 * Finding that out from a terminal in five seconds beats finding it out from a
 * blank panel.
 */
fun main(args: Array<String>): Unit = runBlocking {
    // Joined first: Gradle hands --args over already split on spaces, so a
    // title of more than one word arrives as several arguments.
    val parts = args.joinToString(" ").ifBlank { "Shape of You|Ed Sheeran|233" }.split("|")
    val track = Track(
        id = parts.getOrNull(3) ?: "JGwWNGJdvx8",
        title = parts[0].trim(),
        artist = parts.getOrNull(1)?.trim().orEmpty(),
        thumbnail = null,
        durationSeconds = parts.getOrNull(2)?.trim()?.toIntOrNull(),
    )

    println("Asking for: ${track.title} — ${track.artist} (${track.durationSeconds}s)\n")

    LyricsProviders.all.forEach { provider ->
        val started = System.currentTimeMillis()
        val words = runCatching { provider.find(track) }
        val took = System.currentTimeMillis() - started

        val verdict = when {
            words.isFailure -> "threw ${words.exceptionOrNull()?.javaClass?.simpleName}: " +
                words.exceptionOrNull()?.message?.take(90)
            words.getOrNull().isNullOrBlank() -> "nothing"
            else -> {
                val text = words.getOrNull()!!
                val timed = text.lineSequence().count { it.trimStart().startsWith("[") }
                "${text.length} chars, $timed timed lines"
            }
        }
        println("%-14s %5dms  %s".format(provider.name, took, verdict))
        words.getOrNull()?.takeIf { it.isNotBlank() }?.lineSequence()?.take(3)?.forEach {
            println("               │ $it")
        }
    }
}
