package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.audio.StreamFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** One song kept without being asked for, and when it was last wanted. */
@Serializable
data class Cached(val track: Track, val at: Long, val bytes: Long)

/**
 * The songs you play, kept quietly in case the network isn't there.
 *
 * Downloading is a decision somebody makes in advance, and the trouble with
 * decisions made in advance is that nobody makes them. The moment you need a
 * song offline is the moment the power goes or the wifi drops — which is
 * exactly when you cannot download it, and exactly when you remember you meant
 * to. By then it is too late to be careful.
 *
 * So the songs you actually listen to are kept as you listen to them, with
 * their covers and their words, and thrown away oldest-first when there are
 * too many. Nothing is asked and nothing has to be remembered in advance. It
 * is not a substitute for downloading — a download is a promise that a song
 * will be there, and this is only a good chance — which is why the two are
 * kept apart and shown apart.
 */
object Cache {

    private const val INDEX = "cache.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val folder: File by lazy { File(Store.folder, "cache").apply { mkdirs() } }

    var items by mutableStateOf(emptyList<Cached>())
        private set

    /** Whether to keep anything at all. */
    var on by mutableStateOf(true)
        private set

    /**
     * How much room this is allowed.
     *
     * A cache with no ceiling is not a cache, it is a slow leak — and a music
     * player that quietly eats a disk is one people uninstall rather than
     * configure.
     */
    var limitMegabytes by mutableStateOf(2048)
        private set

    private val settings: File get() = File(Store.folder, "cache-settings")

    init {
        runCatching {
            if (settings.exists()) {
                val lines = settings.readLines()
                on = lines.getOrNull(0) != "false"
                limitMegabytes = lines.getOrNull(1)?.toIntOrNull() ?: 2048
            }
        }
        // Only what is actually still on disk. A file somebody deleted by hand
        // should not leave an entry claiming a song is here.
        items = Store.read<Cached>(INDEX).filter { fileFor(it.track.id).exists() }
        save()
    }

    fun fileFor(id: String) = File(folder, "$id.m4a")

    fun has(id: String) = items.any { it.track.id == id } && fileFor(id).exists()

    /** How much room it is taking, for saying so. */
    val bytes: Long get() = items.sumOf { it.bytes }

    fun choose(keeping: Boolean) {
        on = keeping
        if (!on) forgetAll()
        saveSettings()
    }

    fun chooseLimit(megabytes: Int) {
        limitMegabytes = megabytes.coerceIn(256, 65536)
        saveSettings()
        scope.launch { makeRoom() }
    }

    /**
     * Keep this one, if it is worth keeping and isn't already.
     *
     * Called when a song has been heard rather than when it starts: a track
     * skipped after four seconds is not one somebody will want on a train, and
     * fetching every one of those would spend a morning's data on songs nobody
     * chose.
     */
    fun heard(track: Track) {
        if (!on || track.talk) return
        // A download already promises this one. Two copies of the same audio
        // to make the same song available is a waste of the room the cache is
        // rationing.
        if (Downloads.has(track.id) || LocalMusic.isLocal(track.id)) return
        if (has(track.id)) {
            touch(track.id)
            return
        }
        if (running.contains(track.id)) return

        scope.launch {
            running = running + track.id
            val target = fileFor(track.id)
            val partial = File(folder, "${track.id}.part")

            val where = track.stream?.let { Result.success(it) } ?: Catalogue.streamUrl(track.id)
            where.mapCatching { url -> StreamFetcher.download(url, partial) { }.getOrThrow() }
                .onSuccess {
                    partial.renameTo(target)
                    items = listOf(Cached(track, Instant.now().toEpochMilli(), target.length())) +
                        items.filterNot { it.track.id == track.id }
                    save()
                    // The cover and the words go with it. A song on a dead
                    // connection with no artwork and no lyrics is half the
                    // thing that was being kept.
                    Offline.keep(track)
                    makeRoom()
                }
                .onFailure { runCatching { partial.delete() } }
            running = running - track.id
        }
    }

    /** Songs being fetched right now, so nothing is fetched twice. */
    private var running by mutableStateOf(emptySet<String>())

    /** Note that this one was wanted again, so it survives the next clear-out. */
    private fun touch(id: String) {
        val now = Instant.now().toEpochMilli()
        items = items.map { if (it.track.id == id) it.copy(at = now) else it }
        save()
    }

    /**
     * Turn a kept copy into a promise.
     *
     * Somebody who notices a song here and wants it kept for good should not
     * have to fetch it a second time to say so.
     */
    fun pin(id: String) {
        val kept = items.firstOrNull { it.track.id == id } ?: return
        val from = fileFor(id)
        val to = Downloads.fileFor(id)
        if (from.renameTo(to) || from.copyTo(to, overwrite = true).exists()) {
            Downloads.adopt(kept.track)
            forget(id)
        }
    }

    fun forget(id: String) {
        runCatching { fileFor(id).delete() }
        items = items.filterNot { it.track.id == id }
        save()
    }

    fun forgetAll() {
        items.forEach { runCatching { fileFor(it.track.id).delete() } }
        items = emptyList()
        save()
    }

    /**
     * Throw away the oldest until it fits.
     *
     * Oldest by when it was last played rather than when it arrived: a song
     * kept in January and played every week since is one somebody plainly
     * wants, and a song kept yesterday and never returned to is not.
     */
    private fun makeRoom() {
        val ceiling = limitMegabytes.toLong() * 1024 * 1024
        var total = items.sumOf { it.bytes }
        if (total <= ceiling) return
        val oldestFirst = items.sortedBy { it.at }.toMutableList()
        while (total > ceiling && oldestFirst.isNotEmpty()) {
            val going = oldestFirst.removeAt(0)
            runCatching { fileFor(going.track.id).delete() }
            total -= going.bytes
        }
        items = items.filter { kept -> oldestFirst.any { it.track.id == kept.track.id } }
        save()
    }

    private fun save() = Store.write(INDEX, items)

    private fun saveSettings() {
        runCatching { settings.writeText("$on\n$limitMegabytes") }
    }
}
