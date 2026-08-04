package com.blazify.desktop.tools.podcast

import com.blazify.desktop.data.Feeds
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether the open podcast directory answers, and whether what it hands back
 * would actually play.
 *
 * Three separate things: a search, a chart, and a feed read to the point of a
 * link a player could be given. The last one is the only one that matters —
 * shows nobody can play are a list.
 */
fun main(): Unit = runBlocking {
    println("country taken from this machine: ${Feeds.country}")

    for (words in listOf("nepali", "hindi kahaniya", "cricket", "the daily")) {
        val found = Feeds.search(words, limit = 4)
        println("\n\"$words\" → ${found.size}")
        found.forEach { println("   ${it.title.take(46)} · ${it.author.take(24)} · ${it.episodeCount} eps · ${it.genre}") }
    }

    val chart = Feeds.chart(limit = 8)
    println("\ntop shows here: ${chart.size}")
    chart.forEach { println("   ${it.title.take(46)} · ${it.episodeCount} eps") }

    val show = chart.firstOrNull() ?: Feeds.search("the daily", 1).firstOrNull()
    if (show != null) {
        val episodes = Feeds.episodes(show.feed, limit = 5)
        println("\n${show.title}: ${episodes.size} episodes read from its own feed")
        episodes.take(3).forEach { episode ->
            val track = episode.asTrack()
            println("   ${episode.title.take(50)}")
            println("      ${track.duration} · ${episode.published} · plays from ${track.stream?.take(46)}…")
        }
    }
}
