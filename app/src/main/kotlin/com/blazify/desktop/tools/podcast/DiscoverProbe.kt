package com.blazify.desktop.tools.podcast

import com.blazify.desktop.data.Catalogue
import com.blazify.innertube.YouTube
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What a page of podcasts could be built out of.
 *
 * Asked before the page is designed, because a layout drawn around shelves the
 * catalogue does not hand over is a layout that ships empty. Two questions:
 * what arrives with an account, and what arrives without one.
 */
fun main(): Unit = runBlocking {
    println("the catalogue's own podcast feed:")
    YouTube.podcastDiscover().fold(
        onSuccess = { page ->
            println("  ${page.sections.size} shelves")
            page.sections.forEach { shelf ->
                val kinds = shelf.items.map { it.javaClass.simpleName }.distinct()
                println("    ${shelf.title}: ${shelf.items.size} × $kinds")
            }
        },
        onFailure = { println("  refused — ${it.javaClass.simpleName}") },
    )

    println("\nby subject, which needs no account:")
    for (subject in listOf("news", "comedy", "true crime", "cricket", "bollywood")) {
        val shows = Catalogue.search(subject, Catalogue.Scope.Podcasts).getOrDefault(emptyList())
        val episodes = Catalogue.search(subject, Catalogue.Scope.Episodes).getOrDefault(emptyList())
        println("  $subject → ${shows.size} shows, ${episodes.size} episodes")
        shows.take(2).forEach { println("      ${it.title} — ${it.subtitle}") }
    }

    println("\nwhat needs an account:")
    println("  shows kept: ${YouTube.savedPodcastShows().getOrNull()?.size ?: "refused"}")
    println("  new episodes: ${YouTube.newEpisodes().getOrNull()?.size ?: "refused"}")
    println("  saved for later: ${YouTube.episodesForLater().getOrNull()?.size ?: "refused"}")
    println("  channels: ${YouTube.libraryPodcastChannels().getOrNull()?.items?.size ?: "refused"}")
}
