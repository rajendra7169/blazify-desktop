package com.blazify.desktop.tools

import com.blazify.desktop.data.LyricsSource
import com.blazify.desktop.data.Track
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Look a song's words up and print what came back, timings included. */
fun main(args: Array<String>) = runBlocking {
    val title = args.getOrNull(0) ?: "Kesariya"
    val artist = args.getOrNull(1) ?: "Arijit Singh"
    val seconds = args.getOrNull(2)?.toIntOrNull()

    val lyrics = LyricsSource.of(Track("probe", title, artist, null, seconds))
    println("synced=${lyrics.synced} lines=${lyrics.lines.size} plain=${lyrics.plain?.length ?: 0}")
    lyrics.lines.take(8).forEach { println("  %7.2f  %s".format(it.at, it.text)) }
    println("line at 45s -> ${lyrics.lineAt(45.0)}")
}
