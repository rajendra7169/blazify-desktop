package com.blazify.desktop.data

import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

class WrappedTest {

    private val zone = ZoneOffset.UTC

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun track(id: String, artist: String, seconds: Int?) =
        Track(id = id, title = "Song $id", artist = artist, thumbnail = null, durationSeconds = seconds)

    @Test
    fun theYearThatJustEndedIsShownInEarlyJanuary() {
        assertEquals(2026, Wrapped.defaultYear(LocalDate.of(2027, 1, 3)))
        assertEquals(2027, Wrapped.defaultYear(LocalDate.of(2027, 1, 20)))
        assertEquals(2026, Wrapped.defaultYear(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun midDecemberNamesTheComingYear() {
        assertEquals("2026 so far", Wrapped.caption(2026, LocalDate.of(2026, 12, 1)))
        assertTrue(Wrapped.caption(2026, LocalDate.of(2026, 12, 20)).contains("2027"))
        assertEquals("The whole of 2025", Wrapped.caption(2025, LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun aYearAddsUp() {
        val a = track("a", "Alpha, Guest", 200)
        val b = track("b", "Beta", 100)
        val plays = listOf(
            Play("a", at(2026, 3, 1)),
            Play("a", at(2026, 3, 2)),
            Play("b", at(2026, 5, 1)),
            Play("gone", at(2026, 3, 9)),
            Play("a", at(2025, 3, 1)),
        )
        val year = Wrapped.of(2026, plays, listOf(a, b), zone)

        assertEquals(4, year.plays)
        assertEquals(500, year.seconds)
        assertEquals(3, year.songs)
        assertEquals(2, year.artists)
        assertEquals("a", year.topSongs.first().first.id)
        assertEquals("Alpha", year.topArtists.first().name)
        assertEquals(Month.MARCH to 3, year.bestMonth)
        assertEquals(listOf(2026, 2025), Wrapped.years(plays, zone, LocalDate.of(2026, 6, 1)))
    }
}
