package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** A playlist made here, rather than one fetched from an account. */
@Serializable
data class OwnPlaylist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
) {
    /**
     * The first few covers, for drawing the tile.
     *
     * A playlist has no artwork of its own, and inventing one would be worse
     * than showing what's actually in it.
     */
    val covers: List<String> get() = tracks.mapNotNull { it.thumbnail }.take(4)
}

/**
 * Playlists that belong to this machine.
 *
 * Separate from the account's own, deliberately: these work signed out, they
 * can hold local files the catalogue has never heard of, and nothing about
 * them depends on a session that might expire. Songs are stored whole rather
 * than by id so a playlist still reads properly with the network down.
 */
object Playlists {

    private const val FILE = "playlists.json"

    var all by mutableStateOf(Store.read<OwnPlaylist>(FILE))
        private set

    fun find(id: String) = all.firstOrNull { it.id == id }

    /**
     * Make one.
     *
     * The id is taken from the clock rather than the name, so renaming keeps
     * the playlist and two called "Favourites" stay two playlists.
     */
    fun create(name: String, seed: List<Track> = emptyList()): OwnPlaylist {
        val made = OwnPlaylist(
            id = "own-${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "New playlist" },
            tracks = seed,
        )
        all = listOf(made) + all
        save()
        return made
    }

    fun rename(id: String, name: String) {
        update(id) { it.copy(name = name.trim().ifBlank { it.name }) }
    }

    fun delete(id: String) {
        all = all.filterNot { it.id == id }
        save()
    }

    /** Adding something already there moves nothing — it's already yours. */
    fun add(id: String, track: Track) {
        update(id) { playlist ->
            if (playlist.tracks.any { it.id == track.id }) playlist
            else playlist.copy(tracks = playlist.tracks + track)
        }
    }

    fun removeAt(id: String, position: Int) {
        update(id) { playlist ->
            if (position !in playlist.tracks.indices) playlist
            else playlist.copy(tracks = playlist.tracks.toMutableList().also { it.removeAt(position) })
        }
    }

    /**
     * Put a song somewhere else in the order.
     *
     * The order of a playlist is the point of a playlist — it's the one thing
     * in it that isn't just a list of songs you already have. Moved one step at
     * a time, because that is what a row being dragged past its neighbour is,
     * and rebuilding the list from two indices on every frame would be the
     * same answer arrived at more expensively.
     */
    fun move(id: String, from: Int, to: Int) {
        update(id) { playlist ->
            if (from !in playlist.tracks.indices || to !in playlist.tracks.indices || from == to) playlist
            else playlist.copy(
                tracks = playlist.tracks.toMutableList().also { it.add(to, it.removeAt(from)) },
            )
        }
    }

    fun contains(id: String, trackId: String) = find(id)?.tracks?.any { it.id == trackId } == true

    private fun update(id: String, change: (OwnPlaylist) -> OwnPlaylist) {
        all = all.map { if (it.id == id) change(it) else it }
        save()
    }

    private fun save() = Store.write(FILE, all)
}
