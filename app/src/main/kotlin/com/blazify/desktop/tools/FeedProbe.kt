package com.blazify.desktop.tools.feed

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/** Blazify Project (C) 2026 · Licensed under GPL-3.0 */
fun main(): Unit = runBlocking {
    var token: String? = null
    val seen = mutableSetOf<String>()
    var total = 0

    repeat(5) { page ->
        val feed = Catalogue.home(after = token).getOrElse {
            println("page ${page + 1}: failed — ${it.message}"); return@runBlocking
        }
        val fresh = feed.shelves.filter { seen.add(it.title) }
        total += fresh.size
        println("page ${page + 1}: ${feed.shelves.size} shelves (${fresh.size} new)  more=${feed.more != null}")
        fresh.forEach { println("    ${it.title} (${it.cards.size})") }
        token = feed.more ?: return@runBlocking println("\nfeed ended after ${page + 1} pages, $total shelves")
    }
    println("\n$total shelves across 5 pages, still more to come")
}
