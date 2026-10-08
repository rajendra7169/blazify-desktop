package com.blazify.desktop.data

import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * One line of somebody else's playlist, before it has been found here.
 *
 * Whatever the line said is kept: a link or an id goes straight to the song,
 * a file on this machine is that file, and anything else is a title and an
 * artist to be looked for.
 */
data class Wanted(
    val title: String,
    val artist: String = "",
    val id: String? = null,
    val seconds: Int? = null,
    val path: String? = null,
)

/**
 * Playlists written out to, and read back from, ordinary files.
 *
 * Two formats, because they are the two every other player understands. M3U is
 * what music players swap between themselves; CSV is what a spreadsheet opens,
 * and what most of the export tools for other services hand you. Neither needs
 * anything of this app to be read — that is the point of writing one.
 */
object PlaylistFiles {

    private const val WATCH = "https://music.youtube.com/watch?v="

    // ---- Writing --------------------------------------------------------------

    /**
     * A playlist as M3U.
     *
     * Each song gets the extended line other players show as its name, and
     * then where it lives: the file itself for a song on this computer, a
     * link that opens it for everything else.
     */
    fun m3u(playlist: OwnPlaylist): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(oneLine(playlist.name)).append('\n')
        playlist.tracks.forEach { track ->
            val name = listOf(track.artist, track.title).filter { it.isNotBlank() }.joinToString(" - ")
            append("#EXTINF:").append(track.durationSeconds ?: -1).append(',').append(oneLine(name)).append('\n')
            append(where(track)).append('\n')
        }
    }

    /** A playlist as CSV, with a heading row so a spreadsheet knows the columns. */
    fun csv(playlist: OwnPlaylist): String = buildString {
        append("Title,Artist,Duration,Link\r\n")
        playlist.tracks.forEach { track ->
            append(listOf(track.title, track.artist, track.duration, where(track)).joinToString(",") { cell(it) })
            append("\r\n")
        }
    }

    private fun where(track: Track): String = when {
        LocalMusic.isLocal(track.id) -> LocalMusic.pathOf(track.id)
        track.stream != null -> track.stream
        else -> WATCH + track.id
    }

    private fun oneLine(text: String) = text.replace('\n', ' ').replace('\r', ' ')

    /** Quoted only when it has to be, the way a spreadsheet would write it. */
    private fun cell(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\""
        else text

    // ---- Reading --------------------------------------------------------------

    /**
     * What a file asks for, and the name it gives itself.
     *
     * Told apart by what is inside rather than by the name on the end of the
     * file: an M3U says so on its first line and saved as .txt is still an M3U.
     */
    fun read(file: File): Pair<String, List<Wanted>> {
        val text = file.readText().removePrefix("﻿")
        val fallback = file.nameWithoutExtension
        val looksLikeM3u = text.trimStart().startsWith("#EXTM3U") ||
            file.extension.lowercase() in setOf("m3u", "m3u8")
        return if (looksLikeM3u) {
            val (name, wanted) = parseM3u(text, file.parentFile)
            (name ?: fallback) to wanted
        } else {
            fallback to parseCsv(text)
        }
    }

    /**
     * An M3U, line by line.
     *
     * The line after an #EXTINF is where the song lives, and the #EXTINF says
     * what it's called — which is all there is to look for when where it lived
     * was another player's library on another machine.
     */
    fun parseM3u(text: String, relativeTo: File? = null): Pair<String?, List<Wanted>> {
        var name: String? = null
        var pendingTitle: String? = null
        var pendingSeconds: Int? = null
        val out = mutableListOf<Wanted>()

        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#PLAYLIST:") -> name = line.substringAfter(':').trim().ifBlank { null }
                line.startsWith("#EXTINF:") -> {
                    val info = line.substringAfter(':')
                    pendingSeconds = info.substringBefore(',').trim().substringBefore(' ').toIntOrNull()?.takeIf { it > 0 }
                    pendingTitle = info.substringAfter(',', "").trim().ifBlank { null }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val (artist, title) = split(pendingTitle ?: fileStem(line))
                    val id = videoIdIn(line)
                    val path = if (id == null) localPath(line, relativeTo) else null
                    out += Wanted(title = title, artist = artist, id = id, seconds = pendingSeconds, path = path)
                    pendingTitle = null
                    pendingSeconds = null
                }
            }
        }
        return name to out
    }

    /**
     * A CSV, from this app or anyone else's.
     *
     * The columns are found by their headings, because every exporter names
     * and orders them differently — "Track Name", "Song", "Title", "Artist
     * Name(s)". Without a heading row the first column is taken as the title
     * and the second as the artist, which is how anybody types one by hand.
     */
    fun parseCsv(text: String): List<Wanted> {
        val rows = csvRows(text).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()

        val head = rows.first().map { it.trim().lowercase() }
        fun column(vararg names: String) = head.indexOfFirst { h -> names.any { h == it } }
            .takeIf { it >= 0 }
            ?: head.indexOfFirst { h -> names.any { h.contains(it) } }.takeIf { it >= 0 }

        val title = column("title", "track name", "song", "name", "track")
        val artist = column("artist", "artist name(s)", "artists", "artist name")
        val link = column("link", "url", "uri", "video id", "id")
        val length = column("duration", "length", "time", "duration (ms)")
        val headed = title != null || link != null

        val body = if (headed) rows.drop(1) else rows
        return body.mapNotNull { row ->
            fun at(index: Int?) = index?.let { row.getOrNull(it) }?.trim().orEmpty()
            val t = if (headed) at(title) else at(0)
            val a = if (headed) at(artist) else at(1)
            // A link anywhere on the row counts, but a bare eleven letters only
            // in a column that says it holds ids — "Electronica" is eleven
            // letters too.
            val id = (if (headed) videoIdIn(at(link)) else null)
                ?: row.firstNotNullOfOrNull { cell -> videoIdIn(cell).takeIf { "youtu" in cell } }
            if (t.isBlank() && id == null) return@mapNotNull null
            Wanted(
                title = t,
                artist = a,
                id = id,
                seconds = seconds(at(length), head.getOrNull(length ?: -1)),
            )
        }
    }

    /** "3:25", "205", or milliseconds where the heading says so. */
    private fun seconds(text: String, heading: String?): Int? {
        if (text.isBlank()) return null
        if (':' in text) {
            val parts = text.split(':').map { it.trim().toIntOrNull() ?: return null }
            return parts.fold(0) { total, part -> total * 60 + part }
        }
        val number = text.toLongOrNull() ?: return null
        val millis = heading?.contains("ms") == true || number > 36_000
        return (if (millis) number / 1000 else number).toInt()
    }

    /** Rows of cells, quotes and all — a title with a comma in it is common. */
    internal fun csvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        // A semicolon file, as some spreadsheets save in some countries, is
        // told by its first line having more of those than commas.
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val separator = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','

        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == separator -> { row += cell.toString(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }

    /**
     * The song a link or a bare id points to, if it points to one.
     *
     * Every shape a link to a song turns up in: the music site, the video
     * site, the short links, and the eleven characters on their own.
     */
    fun videoIdIn(text: String): String? {
        val trimmed = text.trim()
        if (BARE_ID.matches(trimmed)) return trimmed
        return LINK_ID.find(trimmed)?.groupValues?.get(1)
    }

    private val BARE_ID = Regex("[A-Za-z0-9_-]{11}")
    private val LINK_ID = Regex(
        "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
    )

    /** "Artist - Title" where it's written that way, otherwise all title. */
    private fun split(text: String): Pair<String, String> {
        val parts = text.split(" - ", limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim()
        else "" to text.trim()
    }

    private fun fileStem(line: String): String =
        line.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').replace('_', ' ')

    /** A path that is really there, written either absolutely or beside the playlist. */
    private fun localPath(line: String, relativeTo: File?): String? {
        if (line.contains("://")) return null
        val direct = File(line)
        val file = if (direct.isAbsolute) direct else relativeTo?.let { File(it, line) } ?: return null
        return file.takeIf { it.isFile }?.absolutePath
    }
}
