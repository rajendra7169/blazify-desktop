package com.blazify.desktop.tools.session

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Whether the catalogue will finish a half-typed name, and a misspelt one. */
fun main(): Unit = runBlocking {
    for (typed in listOf("nusra", "sanson ki", "arjit sing", "cold")) {
        val guesses = Catalogue.suggestions(typed)
        println("\"$typed\" → ${guesses.take(4).joinToString(" · ").ifEmpty { "nothing" }}")
    }
}
