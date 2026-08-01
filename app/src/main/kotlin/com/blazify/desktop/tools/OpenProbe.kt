package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/** Blazify Project (C) 2026 · Licensed under GPL-3.0 */
fun main() = runBlocking {
    val shelves = Catalogue.home().getOrElse { println("home failed: ${it.message}"); return@runBlocking }.shelves
    shelves.take(2).forEach { shelf ->
        println("shelf: ${shelf.title}")
        shelf.cards.take(2).forEach { card ->
            print("  ${card.kind} \"${card.title}\" -> ")
            Catalogue.open(card).fold(
                onSuccess = { tracks ->
                    println("${tracks.size} tracks" + (tracks.firstOrNull()?.let { " (first: ${it.title})" } ?: ""))
                    tracks.firstOrNull()?.let { first ->
                        Catalogue.streamUrl(first.id).fold(
                            onSuccess = { println("      stream ok") },
                            onFailure = { println("      stream failed: ${it.message}") },
                        )
                    }
                },
                onFailure = { println("failed: ${it.message}") },
            )
        }
    }
}
