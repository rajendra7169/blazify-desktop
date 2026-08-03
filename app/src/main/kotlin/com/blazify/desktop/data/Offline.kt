package com.blazify.desktop.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everything a kept song needs when there is no network.
 *
 * The audio was never the whole of it. A song on disk with a grey square where
 * the cover should be, and an empty lyric sheet, is a song you kept and can no
 * longer recognise — so the artwork and the words are fetched at the same time
 * as the audio and stored beside it.
 *
 * Both are small. A cover at full size is a few tens of kilobytes against a
 * four-megabyte song, and a lyric sheet is a couple of kilobytes; neither is
 * worth asking about.
 */
object Offline {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { HttpClient(OkHttp) }

    private val art: File by lazy { File(Store.folder, "art").apply { mkdirs() } }
    private val words: File by lazy { File(Store.folder, "words").apply { mkdirs() } }

    fun artFor(id: String) = File(art, "$id.jpg")
    fun wordsFor(id: String) = File(words, "$id.lrc")

    fun hasArt(id: String) = artFor(id).let { it.exists() && it.length() > 0 }
    fun hasWords(id: String) = wordsFor(id).exists()

    /**
     * The cover, as a path this machine can open.
     *
     * Handed back only when it is genuinely there — a path to a file that does
     * not exist is worse than no path, because the caller stops looking for the
     * one on the network.
     */
    fun artPath(id: String): String? = artFor(id).takeIf { hasArt(it.nameWithoutExtension) }
        ?.toURI()?.toString()

    /**
     * Keep the cover and the words for a song.
     *
     * Called when a song is kept for offline, and again on start-up for
     * anything kept before this existed — a library of songs downloaded last
     * month shouldn't stay coverless because the feature arrived after them.
     */
    fun keep(track: Track) {
        scope.launch {
            if (!hasArt(track.id)) fetchArt(track)
            if (!hasWords(track.id)) fetchWords(track)
        }
    }

    /** Catch up on anything kept before, a few at a time so nothing stalls. */
    fun catchUp() {
        scope.launch {
            Downloads.items.forEach { track ->
                if (!hasArt(track.id)) fetchArt(track)
                if (!hasWords(track.id)) fetchWords(track)
            }
        }
    }

    fun forget(id: String) {
        runCatching { artFor(id).delete() }
        runCatching { wordsFor(id).delete() }
    }

    private suspend fun fetchArt(track: Track) {
        // Asked for at a size worth having. The thumbnail on a list row is
        // fine at 120 pixels; the same picture filling a player is not, and
        // this copy has to serve both because there will be no other.
        val url = track.thumbnail?.let {
            Regex("=w\\d+-h\\d+").replace(it, "=w720-h720")
        } ?: return

        runCatching {
            val target = artFor(track.id)
            val partial = File(target.parentFile, "${target.name}.part")
            client.get(url).bodyAsChannel().copyTo(partial.outputStream())
            // Renamed only once it is whole, so a connection dropped halfway
            // never leaves a truncated image that looks like a real one.
            if (partial.length() > 0) {
                partial.renameTo(target)
                // Point the stored song at its own copy. Done here rather than
                // at every place a cover is drawn: one line means every screen
                // gets the local file, and gets it offline, without any of them
                // having to know this exists.
                Downloads.rememberCover(track.id, target.toURI().toString())
            } else {
                partial.delete()
            }
        }
    }

    private suspend fun fetchWords(track: Track) {
        val found = runCatching { LyricsSource.of(track) }.getOrNull() ?: return
        if (found.empty) return
        val text = when {
            found.synced -> found.lines.joinToString("\n") { line ->
                val seconds = line.at
                "[%02d:%02d.%02d]%s".format(
                    (seconds / 60).toInt(),
                    (seconds % 60).toInt(),
                    ((seconds % 1) * 100).toInt(),
                    line.text,
                )
            }
            else -> found.plain.orEmpty()
        }
        if (text.isNotBlank()) runCatching { wordsFor(track.id).writeText(text) }
    }
}
