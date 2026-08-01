package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.audio.StreamFetcher
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
 * Songs kept on disk so they play without the network.
 *
 * The audio lands beside the rest of the app's data, one file per song named by
 * its id. The list of what's been kept is stored separately, and the two are
 * reconciled on the way in: a file someone deleted by hand shouldn't leave a
 * song in the list that plays silence.
 *
 * A download that fails leaves nothing behind. A half-written file is worse
 * than no file — it plays for ten seconds and stops, which looks like a bug in
 * the player rather than an interrupted download.
 */
object Downloads {

    private const val INDEX = "downloads.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val folder: File by lazy { File(Store.folder, "downloads").apply { mkdirs() } }

    var items by mutableStateOf(emptyList<Track>())
        private set

    /** Songs being fetched right now, and how far along each one is. */
    var running by mutableStateOf(emptyMap<String, Float>())
        private set

    var failure by mutableStateOf<String?>(null)
        private set

    init {
        // Only keep entries whose audio is actually still there.
        items = Store.read<Track>(INDEX).filter { fileFor(it.id).exists() }
        Store.write(INDEX, items)
    }

    /** Audio is mp4, whatever the source called it. */
    fun fileFor(id: String) = File(folder, "$id.m4a")

    fun has(id: String) = items.any { it.id == id } && fileFor(id).exists()

    fun isRunning(id: String) = id in running

    fun progressOf(id: String) = running[id] ?: 0f

    /**
     * Keep a song.
     *
     * Local files are already kept — there's nothing to fetch and nowhere
     * better to put them.
     */
    fun start(track: Track) {
        if (has(track.id) || isRunning(track.id) || LocalMusic.isLocal(track.id)) return
        failure = null
        running = running + (track.id to 0f)

        scope.launch {
            val target = fileFor(track.id)
            val partial = File(folder, "${track.id}.part")

            val done = Catalogue.streamUrl(track.id).mapCatching { url ->
                StreamFetcher.download(url, partial) { fraction ->
                    running = running + (track.id to fraction)
                }.getOrThrow()
            }

            done.fold(
                onSuccess = {
                    // Only becomes the real file once every byte is in it.
                    partial.renameTo(target)
                    items = listOf(track) + items.filterNot { it.id == track.id }
                    Store.write(INDEX, items)
                },
                onFailure = {
                    partial.delete()
                    failure = "Couldn't download ${track.title}"
                },
            )
            running = running - track.id
        }
    }

    fun remove(id: String) {
        fileFor(id).delete()
        items = items.filterNot { it.id == id }
        Store.write(INDEX, items)
    }

    fun removeAll() {
        items.forEach { fileFor(it.id).delete() }
        items = emptyList()
        Store.write(INDEX, items)
    }

    /** How much room the kept songs are taking, in bytes. */
    val bytes: Long
        get() = items.sumOf { fileFor(it.id).length() }
}
