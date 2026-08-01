package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Music already on the machine.
 *
 * Folders are remembered, not copied: pointing at a music library and then
 * having a second copy appear inside the app would be both surprising and
 * expensive. What's stored is a list of places to look and what was last found
 * in them, so the screen fills instantly on launch and only re-reads the disk
 * when asked.
 *
 * A local song is an ordinary [Track] whose id is its path behind a marker, so
 * the queue, the player bar and every list treat it exactly like anything else.
 */
object LocalMusic {

    private const val FOLDERS = "folders.json"
    private const val TRACKS = "local.json"

    /** Marks a track as living on disk rather than in the catalogue. */
    const val PREFIX = "file:"

    fun isLocal(id: String) = id.startsWith(PREFIX)

    fun pathOf(id: String) = id.removePrefix(PREFIX)

    /**
     * What counts as music.
     *
     * The player takes anything, but a music folder is full of artwork, logs
     * and stray text files, and listing those as songs is worse than missing
     * an unusual format.
     */
    private val AUDIO = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "aiff", "aif", "alac",
    )

    var folders by mutableStateOf(Store.read<String>(FOLDERS))
        private set

    var tracks by mutableStateOf(Store.read<Track>(TRACKS))
        private set

    var scanning by mutableStateOf(false)
        private set

    suspend fun add(folder: File) {
        val path = folder.absolutePath
        if (path !in folders) {
            folders = folders + path
            Store.write(FOLDERS, folders)
        }
        rescan()
    }

    fun remove(path: String) {
        folders = folders.filterNot { it == path }
        Store.write(FOLDERS, folders)
        // Dropping a folder should drop its songs with it, or the list would
        // keep offering things it can no longer explain where they came from.
        tracks = tracks.filterNot { pathOf(it.id).startsWith(path) }
        Store.write(TRACKS, tracks)
    }

    /**
     * Walk every folder again.
     *
     * Sorted by artist then title rather than by where the files happen to sit,
     * because a folder layout is how someone stored their music, not how they
     * think about it.
     */
    suspend fun rescan() = withContext(Dispatchers.IO) {
        scanning = true
        val found = folders
            .map(::File)
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .maxDepth(8)      // deep enough for artist/album/disc, shallow enough to end
                    .filter { it.isFile && it.extension.lowercase() in AUDIO }
                    .toList()
            }
            .distinctBy { it.absolutePath }
            .map { it.asTrack() }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))

        tracks = found
        Store.write(TRACKS, found)
        scanning = false
    }

    /**
     * What a file says about itself.
     *
     * Read from the name rather than from tags. Tags would be better when
     * they're present, but plenty of files have none at all, and the naming
     * convention below covers most of a real collection without needing the
     * file opened. Length is left unknown until the player reports it.
     */
    private fun File.asTrack(): Track {
        val stem = nameWithoutExtension.replace('_', ' ').trim()
        // "Artist - Title" is the near-universal convention. A hyphen with no
        // spaces around it is usually part of a word, so only the spaced form
        // counts as a separator.
        val split = stem.split(" - ", limit = 2)
        val (artist, title) =
            if (split.size == 2 && split[0].isNotBlank()) split[0].trim() to split[1].trim()
            else parentFile?.name.orEmpty() to stem

        return Track(
            id = PREFIX + absolutePath,
            title = title.ifBlank { name },
            artist = artist,
            thumbnail = null,
            durationSeconds = null,
        )
    }
}
