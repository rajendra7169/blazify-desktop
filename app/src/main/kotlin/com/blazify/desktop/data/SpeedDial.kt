package com.blazify.desktop.data

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The handful of things somebody plays most, one click away.
 *
 * Built from the play counts rather than fetched, so it is the same on a train
 * with no signal as at home, and it is never a suggestion — everything on it
 * is something you already went back to, again and again.
 *
 * Three kinds side by side: songs, the artists behind them, and playlists made
 * here. A playlist counts its songs' plays at half weight and an artist theirs,
 * because both add up every song in them and would otherwise push the one song
 * you can't stop playing off the grid.
 */
object SpeedDial {

    /** Nine: a three by three grid, which is about what the eye takes in at once. */
    const val SIZE = 9

    sealed interface Dial {
        val key: String
        val score: Double

        data class Song(val track: Track, override val score: Double) : Dial {
            override val key get() = "song-${track.id}"
        }

        /** Somebody, and the songs of theirs you've played most, to start with. */
        data class Artist(
            val name: String,
            val id: String?,
            val picture: String?,
            val songs: List<Track>,
            override val score: Double,
        ) : Dial {
            override val key get() = "artist-$name"
        }

        data class Playlist(val playlist: OwnPlaylist, override val score: Double) : Dial {
            override val key get() = "playlist-${playlist.id}"
        }
    }

    /**
     * What goes on the grid, most played first.
     *
     * The last month where there's enough of it, so the grid follows what
     * somebody is into now; all of time where there isn't, so a new install
     * or a quiet month still has something to show.
     */
    fun build(
        plays: List<Play>,
        known: List<Track>,
        playlists: List<OwnPlaylist>,
        now: Instant = Instant.now(),
    ): List<Dial> {
        if (plays.isEmpty()) return emptyList()
        val monthAgo = now.minus(30, ChronoUnit.DAYS).toEpochMilli()
        val recent = plays.filter { it.at >= monthAgo }
        val counted = (if (recent.map { it.id }.distinct().size >= SIZE) recent else plays)
        val counts = counted.groupingBy { it.id }.eachCount()
        val latest = counted.groupBy { it.id }.mapValues { (_, all) -> all.maxOf { it.at } }

        val songs = known
            .filter { it.id in counts && !it.talk && !Blocked.blocks(it) }
            .sortedWith(
                compareByDescending<Track> { counts[it.id] ?: 0 }.thenByDescending { latest[it.id] ?: 0L },
            )

        val artists = songs
            .filter { it.artist.isNotBlank() }
            .groupBy { Blocked.mainArtist(it) }
            // Somebody is only worth a tile of their own once more than one
            // of their songs is in the rotation; otherwise the song is the tile.
            .filter { (_, theirs) -> theirs.size >= 2 }
            .map { (name, theirs) ->
                Dial.Artist(
                    name = name,
                    id = theirs.firstNotNullOfOrNull { it.artistId },
                    picture = theirs.firstNotNullOfOrNull { it.thumbnail },
                    songs = theirs,
                    score = theirs.sumOf { counts[it.id] ?: 0 } / 2.0,
                )
            }
            .sortedByDescending { it.score }
            .take(3)

        val lists = playlists
            .map { list -> Dial.Playlist(list, list.tracks.sumOf { counts[it.id] ?: 0 } / 2.0) }
            .filter { it.score >= 1.0 }
            .sortedByDescending { it.score }
            .take(2)

        val songDials = songs.take(SIZE).map { Dial.Song(it, (counts[it.id] ?: 0).toDouble()) }

        return (songDials + artists + lists)
            .sortedByDescending { it.score }
            .take(SIZE)
    }
}
