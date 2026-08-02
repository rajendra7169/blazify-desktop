package com.blazify.desktop.tools.discover

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/** Blazify Project (C) 2026 · Licensed under GPL-3.0 */
fun main(): Unit = runBlocking {
    println("${Catalogue.seedCount} seeded shelves available")
    repeat(3) { i ->
        Catalogue.discover(i).fold(
            onSuccess = { println("  ${it.title}: ${it.cards.size} — ${it.cards.firstOrNull()?.title}") },
            onFailure = { println("  seed $i failed: ${it.message}") },
        )
    }
}
