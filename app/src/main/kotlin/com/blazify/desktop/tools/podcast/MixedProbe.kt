package com.blazify.desktop.tools.podcast

import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Feeds
import com.blazify.desktop.data.Origin
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The whole path, through the app rather than around it.
 *
 * A search that reaches both places, a tile opened, an episode taken out of it,
 * and the one question that matters at the end of all of it: would a player
 * have something to play.
 */
fun main(): Unit = runBlocking {
    // The chart, from a place the machine was not going to guess.
    Feeds.chartFrom("np")
    val nepal = Feeds.chart(limit = 6)
    println("top in ${Feeds.country}: ${nepal.joinToString(" · ") { it.title.take(26) }}")
    Feeds.chartFrom("in")
    println("top in ${Feeds.country}: ${Feeds.chart(limit = 6).joinToString(" · ") { it.title.take(26) }}")

    // Following a show and asking its feed what is new, with nobody signed in.
    val followed = Feeds.search("bbc nepali", limit = 1).firstOrNull()
    if (followed != null) {
        val newest = Feeds.episodes(followed.feed, limit = 2)
        println("\nlatest from ${followed.title}:")
        newest.forEach { println("   ${it.title.take(52)} · ${it.asTrack().duration} · ${it.published}") }
    }
    println()

    for (words in listOf("the daily", "nepali", "cricket")) {
        val found = Catalogue.search(words, Catalogue.Scope.Podcasts).getOrDefault(emptyList())
        val fromFeeds = found.count { Feeds.isFeed(it.id) }
        println("\"$words\" → ${found.size} shows (${fromFeeds} from feeds, ${found.size - fromFeeds} from the catalogue)")
        found.take(4).forEach {
            println("   ${if (Feeds.isFeed(it.id)) "feed " else "cat  "} ${it.title.take(46)}")
        }
    }

    // One of each, opened and played from.
    val show = Catalogue.search("the daily", Catalogue.Scope.Podcasts).getOrDefault(emptyList())
        .firstOrNull { Feeds.isFeed(it.id) }
    if (show == null) {
        println("\nno feed show came back — nothing to open")
        return@runBlocking
    }

    println("\nopening ${show.title}")
    Catalogue.collection(show).fold(
        onSuccess = { page ->
            println("  ${page.tracks.size} episodes, note: ${page.note}")
            page.tracks.take(3).forEach { track ->
                println("    ${track.title.take(50)} · ${track.duration} · ${track.from}")
            }
            val first = page.tracks.firstOrNull()
            println("  first episode carries its own link: ${first?.stream != null}")
            println("  and would play without asking the catalogue anything: ${first?.from == Origin.Feed}")
        },
        onFailure = { println("  refused — ${it.javaClass.simpleName}") },
    )
}
