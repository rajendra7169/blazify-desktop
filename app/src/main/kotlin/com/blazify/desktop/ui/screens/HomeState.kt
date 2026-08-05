package com.blazify.desktop.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Store
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Net

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

    private const val KEPT = "home.json"

    /**
     * The feed as it was last time, put back before anything is fetched.
     *
     * A page that is blank until the network answers is a page that is blank
     * on exactly the mornings the network is bad — which is when somebody most
     * wants the thing they were listening to yesterday. What was here before
     * is not stale, it is last night's feed, and last night's feed is a great
     * deal better than a grey rectangle.
     *
     * Replaced the moment the real answer arrives, and only ever shown for the
     * unfiltered feed: a mood is a question that has to be asked afresh.
     */
    private fun remembered(): List<Catalogue.Shelf> =
        runCatching { Store.read<Catalogue.Shelf>(KEPT) }.getOrDefault(emptyList())

    private fun remember(shelves: List<Catalogue.Shelf>) {
        // The first few only. The point is a page that opens with something on
        // it, not a copy of the catalogue on somebody's disk.
        runCatching { Store.write(KEPT, shelves.take(8)) }
    }

    /** Fill the screen, unless it's already full. */
    suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        // Yesterday's, first and instantly, so the window opens on a page
        // rather than on a wait.
        if (shelves.isEmpty() && mood == null) {
            remembered().takeIf { it.isNotEmpty() }?.let {
                shelves = it
                loading = false
            }
        }
        loadFeed()
        buildPicks()
    }

    /**
     * Try the whole page again.
     *
     * The one thing somebody wants after being told a fetch failed, and until
     * now the only way to it was to change page and come back.
     */
    suspend fun again() {
        loaded = true
        loadFeed()
        buildPicks()
    }

    /**
     * The network came back, or went. Either way the page is now the wrong
     * page, and it is built again rather than left saying what used to be true.
     */
    suspend fun reactTo(online: Boolean) {
        if (!loaded) return
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
        // Nothing to recommend from without a catalogue, and the offline
        // shelves already say what there is.
        if (!Net.online) {
            picks = emptyList()
            return
        }
        building = true

        // Songs that only exist on this machine are kept out of the online
        // feed. They are already a screen of their own, and a shelf of them
        // among the catalogue's own is the app showing you your hard disk when
        // you asked it what to listen to. Offline they are all there is, so
        // then they are exactly what belongs here.
        val history = Library.history.filterNot { LocalMusic.isLocal(it.id) }
        val liked = Library.liked.filterNot { LocalMusic.isLocal(it.id) }

        picks = Catalogue.songShelves(history, liked).getOrDefault(emptyList())
        building = false
    }

    private suspend fun loadFeed() {
        // Left standing while the new one is fetched. Clearing first is what
        // makes a page flash empty on every visit, and what somebody is
        // looking at is not wrong until there is something to replace it with.
        loading = shelves.isEmpty()
        problem = null
        discovered = 0

        // No catalogue to ask. What is on this machine is not a poor substitute
        // for the feed — offline it is the whole of what can be played, so it
        // is the feed.
        if (!Net.online) {
            shelves = offlineShelves()
            more = null
            loading = false
            return
        }
        Catalogue.home(mood = mood?.params).fold(
            onSuccess = {
                shelves = it.shelves
                if (mood == null) remember(it.shelves)
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
     * What there is when there is nothing to ask.
     *
     * Kept songs first, then whatever is on this computer, then the part of the
     * history that can still be played — a history row for a song that needs
     * the network is a row that does nothing when tapped.
     */
    private fun offlineShelves(): List<Catalogue.Shelf> = buildList {
        fun shelf(title: String, songs: List<com.blazify.desktop.data.Track>, rows: Int) {
            if (songs.isEmpty()) return
            add(
                Catalogue.Shelf(
                    title = title,
                    cards = songs.take(40).map {
                        Catalogue.Card(
                            it.id, it.title, it.artist, it.thumbnail,
                            Catalogue.Kind.Song, it.durationSeconds,
                        )
                    },
                    rows = rows,
                ),
            )
        }

        shelf("Kept for offline", Downloads.items, rows = 4)
        shelf("On this computer", LocalMusic.tracks, rows = 4)

        val playable = Library.history.filter {
            Downloads.has(it.id) || LocalMusic.isLocal(it.id)
        }
        shelf("Played recently", playable, rows = 1)

        val liked = Library.liked.filter { Downloads.has(it.id) || LocalMusic.isLocal(it.id) }
        shelf("Liked and on this machine", liked, rows = 4)
    }

    /**
     * Add to the bottom of the page.
     *
     * The catalogue's own pages first, then seeded shelves once those run out,
     * so scrolling never hits a dead stop.
     */
    suspend fun extend() {
        if (extending || loading || !Net.online) return
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
