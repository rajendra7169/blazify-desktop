package com.blazify.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Reading other people's playlists, and the links this app makes.
 *
 * Every shape here is one that turns up in real files: exporters disagree on
 * headings, quote titles with commas in them, and write lengths in seconds,
 * minutes or milliseconds.
 */
class PlaylistTransferTest {

    private fun track(id: String, title: String = "t", artist: String = "a", seconds: Int? = null) =
        Track(id = id, title = title, artist = artist, thumbnail = null, durationSeconds = seconds)

    @Test
    fun `an M3U gives its name, its titles and its links`() {
        val (name, songs) = PlaylistFiles.parseM3u(
            """
            #EXTM3U
            #PLAYLIST:Road trip
            #EXTINF:215,Queen - Bohemian Rhapsody
            https://music.youtube.com/watch?v=fJ9rUzIMcZQ
            #EXTINF:-1,Somebody - Something New
            /nowhere/on/this/machine.mp3
            """.trimIndent(),
        )
        assertEquals("Road trip", name)
        assertEquals(2, songs.size)
        assertEquals(Wanted("Bohemian Rhapsody", "Queen", "fJ9rUzIMcZQ", 215), songs[0])
        assertEquals("Something New", songs[1].title)
        assertEquals("Somebody", songs[1].artist)
        assertNull(songs[1].id)
        assertNull(songs[1].path)
    }

    @Test
    fun `a CSV is read by its headings, quotes and all`() {
        val songs = PlaylistFiles.parseCsv(
            "\"Track Name\",\"Artist Name(s)\",\"Duration (ms)\"\n" +
                "\"Hello, Goodbye\",\"The Beatles\",\"208000\"\n" +
                "\"Say \"\"Hi\"\"\",Somebody,61000\n",
        )
        assertEquals(2, songs.size)
        assertEquals(Wanted("Hello, Goodbye", "The Beatles", null, 208), songs[0])
        assertEquals("Say \"Hi\"", songs[1].title)
        assertEquals(61, songs[1].seconds)
    }

    @Test
    fun `a CSV written by this app comes back with its links`() {
        val songs = PlaylistFiles.parseCsv(
            "Title,Artist,Duration,Link\r\n" +
                "Bohemian Rhapsody,Queen,5:55,https://music.youtube.com/watch?v=fJ9rUzIMcZQ\r\n",
        )
        assertEquals(Wanted("Bohemian Rhapsody", "Queen", "fJ9rUzIMcZQ", 355), songs.single())
    }

    @Test
    fun `a CSV without headings is title then artist, and an eleven letter word is not an id`() {
        val songs = PlaylistFiles.parseCsv("Electronica,Somebody\nAnother Song;x\n")
        assertEquals("Electronica", songs[0].title)
        assertEquals("Somebody", songs[0].artist)
        assertNull(songs[0].id)
    }

    @Test
    fun `links to songs are recognised in every shape`() {
        assertEquals("fJ9rUzIMcZQ", PlaylistFiles.videoIdIn("https://www.youtube.com/watch?v=fJ9rUzIMcZQ&t=3"))
        assertEquals("fJ9rUzIMcZQ", PlaylistFiles.videoIdIn("https://youtu.be/fJ9rUzIMcZQ?si=abc"))
        assertEquals("fJ9rUzIMcZQ", PlaylistFiles.videoIdIn("https://music.youtube.com/watch?list=x&v=fJ9rUzIMcZQ"))
        assertEquals("fJ9rUzIMcZQ", PlaylistFiles.videoIdIn("fJ9rUzIMcZQ"))
        assertNull(PlaylistFiles.videoIdIn("https://example.com/song"))
    }

    @Test
    fun `the closest song wins, and nothing close enough means nothing`() {
        val wanted = Wanted("Bohemian Rhapsody - Remastered 2011", "Queen", seconds = 355)
        val found = listOf(
            track("aaaaaaaaaaa", "Bohemian Rhapsody (Karaoke Version)", "Sing King", 360),
            track("bbbbbbbbbbb", "Bohemian Rhapsody", "Queen", 355),
            track("ccccccccccc", "Don't Stop Me Now", "Queen", 210),
        )
        assertEquals("bbbbbbbbbbb", Matching.best(wanted, found)?.id)
        assertNull(Matching.best(Wanted("Completely Different", "Nobody"), found))
    }

    @Test
    fun `Spotify links are recognised, and the embedded player's list is read`() {
        assertEquals(
            Spotify.Ref("playlist", "37i9dQZF1DXcBWIGoYBM5M"),
            Spotify.parse("https://open.spotify.com/intl-de/playlist/37i9dQZF1DXcBWIGoYBM5M?si=1234"),
        )
        assertEquals(Spotify.Ref("album", "4aawyAB9vmqN3uQ7FjRGTy"), Spotify.parse("spotify:album:4aawyAB9vmqN3uQ7FjRGTy"))
        assertNull(Spotify.parse("https://open.spotify.com/track/11hcBLPtbMp4aQI6zGQLub"))

        val page = """
            <html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{
              "type":"playlist","name":"Today’s Top Hits","trackList":[
                {"title":"Patient Zero","subtitle":"Taylor Swift","duration":225868},
                {"title":"Second","subtitle":"Somebody, Someone Else","duration":180000}
            ]}}}}}}</script></html>
        """.trimIndent()
        val (name, songs) = Spotify.fromEmbed(page).getOrThrow()
        assertEquals("Today’s Top Hits", name)
        assertEquals(Wanted("Patient Zero", "Taylor Swift", seconds = 225), songs[0])
        assertEquals(2, songs.size)

        val missing = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"status":404}}}</script>"""
        assertTrue(Spotify.fromEmbed(missing).isFailure)
    }
}
