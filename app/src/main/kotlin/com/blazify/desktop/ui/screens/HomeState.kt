package com.blazify.desktop.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Library

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What the home screen has, kept between visits.
 *
 * Held outside the screen because a screen is thrown away the moment you look
 * at something else, and rebuilding this costs several seconds and a dozen
 * requests. Stepping across to Explore and back should be the same page you
 * left — the same shelves, the same scroll position — not a fresh load and a
 * different set of songs.
 *
 * Refreshing is still possible, and still shuffles; it just isn't the price of
 * changing your mind about which tab you wanted.
 */
object HomeState {

    var picks by mutableStateOf<List<Catalogue.Shelf>>(emptyList())
        private set
    var shelves by mutableStateOf<List<Catalogue.Shelf>>(emptyList())
        private set
    var moods by mutableStateOf<List<Catalogue.Mood>>(emptyList())
        private set
    var mood by mutableStateOf<Catalogue.Mood?>(null)
        private set

    var building by mutableStateOf(true)
        private set
    var loading by mutableStateOf(true)
        private set
    var extending by mutableStateOf(false)
        private set
    var problem by mutableStateOf<String?>(null)
        private set

    private var more: String? = null
    private var discovered = 0
    private var loaded = false

    /** Where the page was scrolled to, so coming back lands where you left. */
    var scroll: LazyListState? = null

    /** Fill the screen, unless it's already full. */
    suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        loadFeed()
        buildPicks()
    }

    /** Throw it away and fetch again — a different twenty songs. */
    suspend fun refresh() {
        loaded = true
        loadFeed()
        buildPicks()
    }

    suspend fun choose(next: Catalogue.Mood?) {
        if (next?.params == mood?.params) return
        mood = next
        loadFeed()
    }

    private suspend fun buildPicks() {
        building = true
        picks = Catalogue.songShelves(Library.history, Library.liked).getOrDefault(emptyList())
        building = false
    }

    private suspend fun loadFeed() {
        loading = true
        problem = null
        shelves = emptyList()
        discovered = 0
        Catalogue.home(mood = mood?.params).fold(
            onSuccess = {
                shelves = it.shelves
                more = it.more
                // Only the unfiltered feed carries the full set; a filtered one
                // answers with fewer, and losing the rest would strand you.
                if (it.moods.isNotEmpty()) moods = it.moods
            },
            onFailure = { problem = "Couldn't reach the catalogue" },
        )
        loading = false
    }

    /**
     * Add to the bottom of the page.
     *
     * The catalogue's own pages first, then seeded shelves once those run out,
     * so scrolling never hits a dead stop.
     */
    suspend fun extend() {
        if (extending || loading) return
        extending = true

        val token = more
        if (token != null) {
            Catalogue.home(after = token, mood = mood?.params).onSuccess { next ->
                // Shelves repeat across pages often enough to notice; keeping
                // them out is cheaper than letting the feed stutter.
                val seen = shelves.map { it.title }.toSet()
                shelves = shelves + next.shelves.filter { it.title !in seen }
                more = next.more
            }
        } else if (discovered < Catalogue.seedCount) {
            val next = discovered
            discovered += 1
            Catalogue.discover(next).onSuccess { shelf ->
                if (shelf.cards.isNotEmpty()) shelves = shelves + shelf
            }
        }
        extending = false
    }
}
