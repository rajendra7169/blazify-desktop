package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Somebody you'd rather not hear. */
@Serializable
data class BlockedArtist(val name: String, val id: String? = null)

/**
 * Artists kept out of everything chosen for you.
 *
 * Home, radio, what plays on after a queue runs out, quick picks and search
 * all leave them out. What you choose yourself is left alone: opening their
 * album and pressing play still plays it, because a block is about what the
 * app suggests, not about what you're allowed to listen to.
 *
 * Matched by id where the catalogue gave one and by name otherwise, since it
 * leaves the id off often enough that an id alone would let half their songs
 * through. Names are compared one artist at a time, so blocking somebody also
 * catches the songs they're featured on — which is usually the point.
 */
object Blocked {

    private const val FILE = "blocked.json"

    var all by mutableStateOf(Store.read<BlockedArtist>(FILE))
        private set

    fun isBlocked(name: String, id: String? = null): Boolean {
        if (all.isEmpty()) return false
        if (id != null && all.any { it.id == id }) return true
        val key = name.trim().lowercase()
        return key.isNotEmpty() && all.any { it.name.lowercase() == key }
    }

    /** Block somebody, or unblock them if they already are. */
    fun toggle(name: String, id: String?) {
        if (isBlocked(name, id)) unblock(name, id) else block(name, id)
    }

    fun block(name: String, id: String?) {
        if (name.isBlank() || isBlocked(name, id)) return
        all = listOf(BlockedArtist(name.trim(), id)) + all
        Store.write(FILE, all)
    }

    fun unblock(name: String, id: String?) {
        val key = name.trim().lowercase()
        all = all.filterNot { (id != null && it.id == id) || it.name.lowercase() == key }
        Store.write(FILE, all)
    }

    /** Whether a song is by, or features, anybody blocked. */
    fun blocks(track: Track): Boolean =
        all.isNotEmpty() && (isBlocked("", track.artistId) || names(track.artist).any { isBlocked(it) })

    /**
     * Whether a tile is somebody blocked, or theirs.
     *
     * Playlists are left alone — one blocked artist among fifty others is not
     * a reason to hide somebody else's list.
     */
    fun blocks(card: Catalogue.Card): Boolean = all.isNotEmpty() && when (card.kind) {
        Catalogue.Kind.Artist -> isBlocked(card.title, card.id)
        Catalogue.Kind.Song, Catalogue.Kind.Album -> names(card.subtitle).any { isBlocked(it) }
        Catalogue.Kind.Playlist -> false
    }

    /** Shelves with the blocked taken out, and shelves left with nothing dropped. */
    fun shelves(shelves: List<Catalogue.Shelf>): List<Catalogue.Shelf> {
        if (all.isEmpty()) return shelves
        return shelves.mapNotNull { shelf ->
            val kept = shelf.cards.filterNot { blocks(it) }
            if (kept.isEmpty()) null else if (kept.size == shelf.cards.size) shelf else shelf.copy(cards = kept)
        }
    }

    /**
     * The separate people in a credit.
     *
     * "A, B & C feat. D" is four artists, and blocking D has to find D in it.
     * The whole credit is kept as well as its parts, because "Simon &
     * Garfunkel" is one act and not two people. The bullet the catalogue puts
     * before an album name or a year is the end of the credit, not another
     * artist.
     */
    internal fun names(credit: String): List<String> {
        val whole = credit.substringBefore(" • ").trim()
        val parts = whole.split(", ", " & ", " x ", " feat. ", " ft. ", " featuring ", " and ", " with ")
        return (listOf(whole) + whole.split(", ") + parts).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /**
     * Who a song is by, for blocking from its menu: the first credited artist.
     *
     * The catalogue joins several artists with commas, so the first of those
     * is the one the song is filed under.
     */
    fun mainArtist(track: Track): String = track.artist.substringBefore(" • ").split(", ").first().trim()
}
