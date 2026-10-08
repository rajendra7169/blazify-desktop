package com.blazify.desktop.data

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A year of listening, added up.
 *
 * Built from the same timestamps the top lists use, so it never disagrees with
 * them. Time is the songs' lengths added together: a play is only counted once
 * most of a song has been heard, so the length is a fair stand-in for what was
 * listened to, and a song whose length was never known adds nothing rather
 * than a guess.
 */
object Wrapped {

    /** The day the year is treated as complete enough to be looked back on. */
    private const val WRAP_DAY = 15

    data class Year(
        val year: Int,
        val plays: Int,
        val seconds: Long,
        val songs: Int,
        val artists: Int,
        val topSongs: List<Pair<Track, Int>>,
        val topArtists: List<Plays.Tally>,
        /** The busiest month and how many plays it had, or null for an empty year. */
        val bestMonth: Pair<Month, Int>?,
        /** Plays per month, January first, for drawing the shape of the year. */
        val months: List<Int>,
    )

    /**
     * Which year to open on.
     *
     * The year that is running — except in the first half of January, when the
     * new one has a handful of plays and the one that just ended is the one
     * people come here to see.
     */
    fun defaultYear(today: LocalDate = LocalDate.now()): Int =
        if (today.monthValue == 1 && today.dayOfMonth < WRAP_DAY) today.year - 1 else today.year

    /** Every year worth offering: the ones with plays in them, and the default. */
    fun years(all: List<Play> = Plays.all, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now()): List<Int> =
        (all.map { Instant.ofEpochMilli(it.at).atZone(zone).year } + defaultYear(today))
            .distinct()
            .sortedDescending()

    /**
     * How the year is described at the top of the page.
     *
     * A year still running is "so far"; from the middle of December it is
     * wrapped, and the coming one is named as the next to start counting.
     */
    fun caption(year: Int, today: LocalDate = LocalDate.now()): String = when {
        year < today.year -> "The whole of $year"
        year > today.year -> "$year hasn't started yet"
        today.monthValue == 12 && today.dayOfMonth >= WRAP_DAY ->
            "$year, wrapped  ·  ${year + 1} starts counting on 1 January"
        else -> "$year so far"
    }

    fun monthName(month: Month): String = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /**
     * Add one year up.
     *
     * The songs are matched against everything the app has seen, the same way
     * the top lists are. Plays of songs that can no longer be found still count
     * towards the totals — they happened — but can't be named in a list.
     */
    fun of(
        year: Int,
        all: List<Play> = Plays.all,
        known: List<Track> = Library.known(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Year {
        val within = all.filter { Instant.ofEpochMilli(it.at).atZone(zone).year == year }
        val byId = known.associateBy { it.id }

        val counts = within.groupingBy { it.id }.eachCount()
        val latest = within.groupBy { it.id }.mapValues { (_, plays) -> plays.maxOf { it.at } }

        val topSongs = counts.mapNotNull { (id, count) -> byId[id]?.let { it to count } }
            .sortedWith(
                compareByDescending<Pair<Track, Int>> { it.second }
                    .thenByDescending { latest[it.first.id] ?: 0L },
            )

        val topArtists = topSongs
            .filter { it.first.artist.isNotBlank() }
            .groupBy { it.first.artist.substringBefore(",").trim() }
            .map { (name, rows) ->
                Plays.Tally(
                    name = name,
                    id = rows.firstNotNullOfOrNull { it.first.artistId },
                    thumbnail = rows.firstNotNullOfOrNull { it.first.thumbnail },
                    plays = rows.sumOf { it.second },
                    songs = rows.size,
                )
            }
            .sortedWith(compareByDescending<Plays.Tally> { it.plays }.thenByDescending { it.songs })

        val months = MutableList(12) { 0 }
        within.forEach { months[Instant.ofEpochMilli(it.at).atZone(zone).monthValue - 1]++ }
        val busiest = months.indices.maxByOrNull { months[it] }?.takeIf { months[it] > 0 }

        return Year(
            year = year,
            plays = within.size,
            seconds = within.sumOf { (byId[it.id]?.durationSeconds ?: 0).toLong() },
            songs = counts.size,
            artists = topArtists.size,
            topSongs = topSongs.take(5),
            topArtists = topArtists.take(5),
            bestMonth = busiest?.let { Month.of(it + 1) to months[it] },
            months = months,
        )
    }
}
