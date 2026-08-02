package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Build the song shelves twice, to check they differ between refreshes. */
fun main() = runBlocking {
    repeat(2) { round ->
        println("--- refresh ${round + 1} ---")
        Catalogue.songShelves(emptyList()).fold(
            onSuccess = { shelves ->
                if (shelves.isEmpty()) println("  nothing came back")
                shelves.forEach { shelf: Catalogue.Shelf ->
                    println("  ${shelf.title}  (${shelf.cards.size} songs, rows=${shelf.rows})")
                    shelf.cards.take(3).forEach { card -> println("     " + card.title + " — " + card.subtitle) }
                }
            },
            onFailure = { println("  failed: ${it.message}") },
        )
    }
}
