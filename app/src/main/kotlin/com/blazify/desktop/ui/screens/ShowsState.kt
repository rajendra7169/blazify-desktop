package com.blazify.desktop.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everything the podcasts page is made of, fetched once.
 *
 * Half of it needs an account and half of it doesn't, and the page has to be
 * worth opening either way — so the two halves are fetched independently and
 * the page draws whichever arrived. Kept between visits like the home feed:
 * programmes do not change between one glance and the next.
 */
object ShowsState {

    /**
     * The subjects on offer.
     *
     * Chosen rather than fetched, because the catalogue has no list of podcast
     * genres to ask for — it will answer a search for any of these and answers
     * nothing at all to a request for the set. Broad enough to cover an
     * evening, and ordered so the first one is the one most people mean.
     */
    val subjects = listOf(
        "News", "True crime", "Comedy", "Business", "Technology",
        "Health", "History", "Sport", "Cricket", "Bollywood", "Science", "Football",
    )

    var feed by mutableStateOf<List<Catalogue.Shelf>>(emptyList())
        private set
    var fresh by mutableStateOf<List<Track>>(emptyList())
        private set
    var later by mutableStateOf<List<Track>>(emptyList())
        private set
    var channels by mutableStateOf<List<Catalogue.Card>>(emptyList())
        private set

    var subject by mutableStateOf(subjects.first())
        private set
    var subjectShows by mutableStateOf<List<Catalogue.Card>>(emptyList())
        private set
    var subjectEpisodes by mutableStateOf<List<Catalogue.Card>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set
    var loadingSubject by mutableStateOf(false)
        private set

    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        loading = true
        coroutineScope {
            // All at once. They are four unrelated questions to four different
            // parts of the catalogue, and asking them in turn would make the
            // page as slow as the sum of them rather than the slowest.
            val theFeed = async { Catalogue.showFeed() }
            val theFresh = async { Catalogue.freshEpisodes() }
            val theLater = async { Catalogue.episodesForLater() }
            val theChannels = async { Catalogue.showChannels() }
            feed = theFeed.await()
            fresh = theFresh.await()
            later = theLater.await()
            channels = theChannels.await()
        }
        loading = false
        choose(subject)
    }

    /**
     * Look at a subject.
     *
     * Asked for as "News podcast" rather than "News", which is not a
     * superstition — measured against the catalogue, the bare word brings back
     * whoever happened to use it in a title, and the longer phrase brings back
     * the programmes people mean. "Cricket" opens with a channel of Nepali
     * scorecards; "Cricket podcast" opens with Stick to Cricket and the
     * Telegraph's.
     */
    suspend fun choose(picked: String) {
        subject = picked
        loadingSubject = true
        val asked = "$picked podcast"
        coroutineScope {
            val shows = async { Catalogue.search(asked, Catalogue.Scope.Podcasts).getOrDefault(emptyList()) }
            val episodes = async { Catalogue.search(asked, Catalogue.Scope.Episodes).getOrDefault(emptyList()) }
            subjectShows = shows.await()
            subjectEpisodes = episodes.await()
        }
        loadingSubject = false
    }

    /** Ask again — after signing in, when half of this becomes available. */
    fun forget() {
        loaded = false
        feed = emptyList()
        fresh = emptyList()
        later = emptyList()
        channels = emptyList()
    }
}
