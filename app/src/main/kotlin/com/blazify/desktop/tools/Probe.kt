package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Searches, then resolves the first hit, printing what came back. */
fun main(args: Array<String>) = runBlocking {
    val queries = if (args.isEmpty()) listOf("sushant kc dashain tihar") else listOf(args.joinToString(" "))

    queries.forEach { query ->
        println("search: \"$query\"")
        Catalogue.search(query).fold(
            onSuccess = { tracks ->
                println("  ${tracks.size} results")
                tracks.take(3).forEach { println("   · ${it.title} — ${it.artist}  ${it.duration}  [${it.id}]") }
                tracks.firstOrNull()?.let { first ->
                    Catalogue.streamUrl(first.id).fold(
                        onSuccess = {
                        println("  stream ok")
                        java.io.File("/tmp/blazify-url.txt").writeText(it)
                    },
                        onFailure = { println("  stream failed: ${it.message}") },
                    )
                }
            },
            onFailure = { println("  failed: ${it.message}") },
        )
    }
}
