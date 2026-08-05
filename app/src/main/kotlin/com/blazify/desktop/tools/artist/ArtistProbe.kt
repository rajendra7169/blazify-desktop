package com.blazify.desktop.tools.artist

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** What an artist page actually comes back with, before a screen is built on it. */
fun main(): Unit = runBlocking {
    for (name in listOf("nusrat fateh ali khan", "arijit singh")) {
        val artist = Catalogue.search(name, Catalogue.Scope.Artists).getOrDefault(emptyList()).firstOrNull()
        if (artist == null) { println("$name: not found"); continue }
        Catalogue.collection(artist).fold(
            onSuccess = { page ->
                println("\n${page.card.title}")
                println("  note: ${page.note}")
                println("  about: ${page.about?.take(130)?.replace("\n", " ") ?: "none"}")
                println("  shelves: ${page.shelves.map { "${it.title}(${it.cards.size})" }}")
            },
            onFailure = { println("$name: refused") },
        )
    }
}
