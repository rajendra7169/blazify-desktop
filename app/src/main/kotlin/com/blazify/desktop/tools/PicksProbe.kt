package com.blazify.desktop.tools.picks

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Build the song shelves twice, to check they differ between refreshes. */
fun main(): Unit = runBlocking {
    repeat(2) { round ->
        val began = System.currentTimeMillis()
        println("--- refresh ${round + 1} ---")
        Catalogue.songShelves(emptyList(), emptyList()).fold(
            onSuccess = { shelves ->
                if (shelves.isEmpty()) println("  nothing came back")
                shelves.forEach { shelf: Catalogue.Shelf ->
                    println("  ${shelf.title}  (${shelf.cards.size} songs, rows=${shelf.rows})")
                    shelf.cards.take(3).forEach { card -> println("     " + card.title + " — " + card.subtitle) }
                }
            },
            onFailure = { println("  failed: ${it.message}") },
        )
        println("  took ${(System.currentTimeMillis() - began) / 1000.0}s")
    }
}
