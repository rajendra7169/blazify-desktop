package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.ui.ArtworkColour
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Pull the accent out of a few real covers and print what came back. */
fun main(args: Array<String>) = runBlocking {
    val query = if (args.isEmpty()) "arijit singh" else args.joinToString(" ")
    val tracks = Catalogue.search(query).getOrNull().orEmpty().take(5)
    if (tracks.isEmpty()) { println("no results"); return@runBlocking }

    for (track in tracks) {
        val accent = track.thumbnail?.let { ArtworkColour.extract(it) }
        println(
            "%-42s %s".format(
                track.title.take(42),
                accent?.let { "#%06X → #%06X".format(it.head and 0xFFFFFF, it.tail and 0xFFFFFF) }
                    ?: "no colour found",
            ),
        )
    }
}
