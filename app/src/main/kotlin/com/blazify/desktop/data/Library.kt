package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The songs you liked, the ones you played, and the collections you kept.
 *
 * Held in memory and mirrored to disk on every change. The lists are small and
 * writing one is a few kilobytes, so there's no need to batch it — and doing it
 * immediately means a crash never costs you the last thing you liked.
 *
 * Newest first throughout, since that's the order every screen wants to show
 * and re-sorting on the way out would only be undone on the way in.
 */
object Library {

    private const val LIKED = "liked.json"
    private const val HISTORY = "history.json"
    private const val SAVED = "saved.json"

    /** Long enough to look back through, short enough to stay a quick read. */
    private const val HISTORY_LIMIT = 500

    var liked by mutableStateOf(Store.read<Track>(LIKED))
        private set

    var history by mutableStateOf(Store.read<Track>(HISTORY))
        private set

    var saved by mutableStateOf(Store.read<Catalogue.Card>(SAVED))
        private set

    fun isLiked(id: String) = liked.any { it.id == id }

    fun toggleLike(track: Track) {
        liked = if (isLiked(track.id)) liked.filterNot { it.id == track.id }
        else listOf(track) + liked
        Store.write(LIKED, liked)
    }

    /**
     * Note that something was played.
     *
     * A song you return to shouldn't appear five times — the old entry is
     * dropped and it moves back to the top, so the list reads as "when did I
     * last hear this" rather than a tally.
     */
    fun played(track: Track) {
        if (history.firstOrNull()?.id == track.id) return
        history = (listOf(track) + history.filterNot { it.id == track.id }).take(HISTORY_LIMIT)
        Store.write(HISTORY, history)
    }

    fun clearHistory() {
        history = emptyList()
        Store.write(HISTORY, history)
    }

    fun isSaved(id: String) = saved.any { it.id == id }

    fun toggleSaved(card: Catalogue.Card) {
        saved = if (isSaved(card.id)) saved.filterNot { it.id == card.id }
        else listOf(card) + saved
        Store.write(SAVED, saved)
    }
}
