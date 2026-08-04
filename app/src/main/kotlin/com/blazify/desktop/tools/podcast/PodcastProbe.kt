package com.blazify.desktop.tools.podcast

import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What the app makes of a podcast, from the search box to a playable link.
 *
 * Three questions that look like one: whether a search finds them, whether
 * opening one lists its episodes, and whether an episode resolves to something
 * a player can be handed. Asked through the app's own path rather than the
 * catalogue's, because a result the app then drops is not a result.
 */
fun main(): Unit = runBlocking {
    val query = "the daily"

    val podcasts = Catalogue.search(query, Catalogue.Scope.Podcasts).getOrElse {
        println("podcast search refused — ${it.javaClass.simpleName}"); emptyList()
    }
    println("podcasts: ${podcasts.size}")
    podcasts.take(3).forEach { println("  ${it.title} — ${it.subtitle} (${it.kind})") }

    podcasts.firstOrNull()?.let { card ->
        println("\nopening ${card.title}")
        Catalogue.collection(card).fold(
            onSuccess = { page ->
                println("  ${page.tracks.size} episodes, note: ${page.note}")
                page.tracks.take(3).forEach { println("    ${it.title}") }
                page.tracks.firstOrNull()?.let { first ->
                    Catalogue.stream(first.id).fold(
                        onSuccess = { println("  first episode PLAYS") },
                        onFailure = { println("  first episode has no stream — ${it.message?.take(120)}") },
                    )
                }
            },
            onFailure = { println("  refused — ${it.javaClass.simpleName}") },
        )
    }

    val episodes = Catalogue.search(query, Catalogue.Scope.Episodes).getOrElse {
        println("episode search refused — ${it.javaClass.simpleName}"); emptyList()
    }
    println("\nepisodes: ${episodes.size}")
    episodes.take(3).forEach { println("  ${it.title} — ${it.subtitle} (${it.kind})") }
    episodes.firstOrNull()?.let { card ->
        Catalogue.stream(card.id).fold(
            onSuccess = { println("  first PLAYS") },
            onFailure = { println("  first has no stream — ${it.message?.take(120)}") },
        )
    }
}
