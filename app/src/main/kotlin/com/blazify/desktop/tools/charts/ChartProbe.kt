package com.blazify.desktop.tools.charts

import com.blazify.innertube.YouTube
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** What the catalogue's own charts come back with. */
fun main(): Unit = runBlocking {
    // With whatever session this machine holds: the charts are one of the
    // things the catalogue answers differently for somebody it knows.
    com.blazify.desktop.data.Account.restore()
    kotlinx.coroutines.delay(9000)
    println("signed in: ${com.blazify.desktop.data.Account.signedIn}")

    // The dedicated charts call answers with nothing. The ordinary browse of
    // the same page, read the way every other page here is read, is worth a
    // try before anything is built on either.
    for (params in listOf(null, "ggMGCgQIgAQ%3D", "sgYKGgQIARABGgQIAhAB")) {
        val genre = com.blazify.desktop.data.Catalogue.Genre("Charts", 0L, "FEmusic_charts", params)
        val shelves = com.blazify.desktop.data.Catalogue.genre(genre).getOrDefault(emptyList())
        println("browse with params=${params ?: "none"} → ${shelves.size} shelves")
        shelves.take(6).forEach { println("    ${it.title}: ${it.cards.size} (${it.cards.firstOrNull()?.kind})") }
    }

    // The chart shelves hold playlists rather than songs. Which playlists?
    val shelves = com.blazify.desktop.data.Catalogue
        .genre(com.blazify.desktop.data.Catalogue.Genre("Charts", 0L, "FEmusic_charts", null))
        .getOrDefault(emptyList())
    shelves.firstOrNull { it.cards.any { c -> c.kind == com.blazify.desktop.data.Catalogue.Kind.Playlist } }
        ?.cards?.forEach { card ->
            println("\nchart playlist: ${card.title} — ${card.subtitle} (${card.id})")
            com.blazify.desktop.data.Catalogue.collection(card).fold(
                onSuccess = { page ->
                    println("   ${page.tracks.size} songs")
                    page.tracks.take(5).forEach { println("      ${it.title} — ${it.artist}") }
                },
                onFailure = { println("   refused") },
            )
        }

    YouTube.getChartsPage().fold(
        onSuccess = { page ->
            println("${page.sections.size} sections")
            page.sections.forEach { section ->
                println("  ${section.title}: ${section.items.size} × ${section.items.map { it.javaClass.simpleName }.distinct()}")
                section.items.take(3).forEach { println("      ${it.title}") }
            }
        },
        onFailure = { println("refused — ${it.javaClass.simpleName}: ${it.message?.take(120)}") },
    )
}
