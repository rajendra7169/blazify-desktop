package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** One song, and the moment it was played. */
@Serializable
data class Play(val id: String, val at: Long)

/**
 * How often, and when.
 *
 * The history already said what you played and in what order, but not how many
 * times — so "the song I have had on all month" was a thing the app knew and
 * could not tell you. This keeps the timestamps instead of only the order,
 * which is the difference between a list and an answer.
 *
 * Stored as bare ids and longs. The songs themselves are already in the
 * history, and keeping a second copy of every title would double the file for
 * nothing.
 */
object Plays {

    private const val FILE = "plays.json"

    /** Two years of listening is a few hundred kilobytes and answers every window. */
    private const val LIMIT = 20_000

    var all by mutableStateOf(Store.read<Play>(FILE))
        private set

    /** The windows worth asking about. */
    enum class Span(val label: String, val days: Long?) {
        Week("This week", 7),
        Month("This month", 30),
        Year("This year", 365),
        Ever("All time", null),
    }

    fun note(track: Track) {
        all = (all + Play(track.id, Instant.now().toEpochMilli())).takeLast(LIMIT)
        Store.write(FILE, all)
    }

    fun forget() {
        all = emptyList()
        Store.write(FILE, all)
    }

    /**
     * The most played songs in a window, most first.
     *
     * Ties are broken by whichever was played most recently, so two songs on
     * four plays each don't swap places every time the screen is drawn.
     */
    fun top(span: Span, from: List<Track>, limit: Int = 100): List<Pair<Track, Int>> {
        val since = span.days?.let {
            Instant.now().minus(it, ChronoUnit.DAYS).toEpochMilli()
        } ?: 0L

        val within = all.filter { it.at >= since }
        if (within.isEmpty()) return emptyList()

        val counts = within.groupingBy { it.id }.eachCount()
        val latest = within.groupBy { it.id }.mapValues { (_, plays) -> plays.maxOf { it.at } }

        return from.mapNotNull { track ->
            val count = counts[track.id] ?: return@mapNotNull null
            track to count
        }.sortedWith(
            compareByDescending<Pair<Track, Int>> { it.second }
                .thenByDescending { latest[it.first.id] ?: 0L },
        ).take(limit)
    }

    /** How many plays are on record in a window, for saying so out loud. */
    fun countIn(span: Span): Int {
        val since = span.days?.let {
            Instant.now().minus(it, ChronoUnit.DAYS).toEpochMilli()
        } ?: 0L
        return all.count { it.at >= since }
    }
}
