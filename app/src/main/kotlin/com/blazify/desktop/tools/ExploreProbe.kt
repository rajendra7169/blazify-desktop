package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Print the browse tab, then open the first genre and show what's inside. */
fun main() = runBlocking {
    val explore = Catalogue.explore().getOrElse {
        println("couldn't reach the catalogue: ${it.message}")
        return@runBlocking
    }
    println("new releases: ${explore.releases.size}")
    explore.releases.take(4).forEach { println("  ${it.title} — ${it.subtitle}") }
    println("genres: ${explore.genres.size}")
    explore.genres.take(8).forEach { println("  ${it.title}") }

    val first = explore.genres.firstOrNull() ?: return@runBlocking
    println("\ninside \"${first.title}\":")
    Catalogue.genre(first).fold(
        onSuccess = { shelves ->
            shelves.take(5).forEach { println("  ${it.title}  (${it.cards.size}, rows=${it.rows})") }
        },
        onFailure = { println("  failed: ${it.message}") },
    )
}
