package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Run one query through every kind of search and print what each returns. */
fun main(args: Array<String>) = runBlocking {
    val query = if (args.isEmpty()) "arijit singh" else args.joinToString(" ")
    println("query: $query")
    Catalogue.Scope.entries.forEach { scope ->
        val found = Catalogue.search(query, scope)
        found.fold(
            onSuccess = { cards ->
                println("%-10s %3d  %s".format(scope.label, cards.size, cards.firstOrNull()?.let {
                    "${it.kind}  ${it.title} — ${it.subtitle}"
                } ?: "nothing"))
            },
            onFailure = { println("%-10s failed: ${it.message}".format(scope.label)) },
        )
    }
}
