package com.blazify.desktop.tools.captions

import com.blazify.desktop.data.Captions
import com.blazify.desktop.data.Feeds
import com.blazify.desktop.data.Origin
import com.blazify.desktop.data.Track
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether an episode's words can be had, and in the shape the panel reads.
 *
 * Both halves matter: a show that publishes them, and one that does not. A
 * source that answers for everything is a source that is making things up.
 */
fun main(): Unit = runBlocking {
    for (name in listOf("linux unplugged", "the daily")) {
        val show = Feeds.search(name, limit = 1).firstOrNull()
        if (show == null) { println("$name: not found"); continue }
        val episode = Feeds.episodes(show.feed, limit = 1).firstOrNull()
        if (episode == null) { println("$name: no episodes"); continue }

        val track = episode.asTrack()
        println("\n${show.title}: ${episode.title.take(48)}")
        println("  feed offers words: ${track.words != null}")

        val words = Captions.find(track)
        if (words == null) {
            println("  nothing to show")
        } else {
            val lines = words.lines()
            val timed = lines.count { it.startsWith("[") }
            println("  ${lines.size} lines, ${timed} of them timed")
            lines.take(4).forEach { println("      ${it.take(78)}") }
        }
    }

    // The guards: a song, and talk with nothing published for it.
    val song = Track("abc", "A song", "Someone", null, 200, spoken = false, words = "http://x")
    println("\na song asks → ${Captions.find(song) ?: "nothing, as intended"}")
    val bare = Track("d", "An episode", "A show", null, 3600, spoken = true, from = Origin.Feed)
    println("talk with nothing published asks → ${Captions.find(bare) ?: "nothing, as intended"}")
}
