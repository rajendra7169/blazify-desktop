package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Print the moods the feed offers, then fetch one and show what changed. */
fun main() = runBlocking {
    val feed = Catalogue.home().getOrElse {
        println("couldn't reach the catalogue: ${it.message}")
        return@runBlocking
    }
    println("moods: ${feed.moods.joinToString { it.title }}")
    println("plain feed:")
    feed.shelves.take(6).forEach { println("  ${it.title}  (${it.cards.size}, rows=${it.rows})") }

    val pick = feed.moods.firstOrNull { it.params != null } ?: return@runBlocking
    println("\nmood \"${pick.title}\":")
    Catalogue.home(mood = pick.params).onSuccess { filtered ->
        filtered.shelves.take(6).forEach { println("  ${it.title}  (${it.cards.size}, rows=${it.rows})") }
    }
}
