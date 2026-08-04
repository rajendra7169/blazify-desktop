package com.blazify.desktop.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Feeds
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Store
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

    /**
     * Where programmes are looked for.
     *
     * The two know different things and it is not close: the open directory has
     * the world's programmes with hundreds of episodes each and audio anybody
     * can play, and the music catalogue has the local ones whose makers never
     * registered a feed and publish where their audience already is. Measured
     * both ways. So the honest default is both, and the choice is here for
     * somebody who wants one of them and knows why.
     */
    enum class Where(val label: String) { Both("Both"), Directory("Apple"), Catalogue("YouTube") }

    private val kept: java.io.File get() = java.io.File(Store.folder, "podcast-source")

    var where by mutableStateOf(
        runCatching { Where.valueOf(kept.readText().trim()) }.getOrDefault(Where.Both),
    )
        private set

    /** What this place is listening to, which needs nobody signed in. */
    var chart by mutableStateOf<List<Catalogue.Card>>(emptyList())
        private set

    var feed by mutableStateOf<List<Catalogue.Shelf>>(emptyList())
        private set
    var fresh by mutableStateOf<List<Track>>(emptyList())
        private set

    /**
     * The latest from the programmes somebody follows here.
     *
     * Read from the feeds themselves, which is the only reason this can exist
     * at all without an account: following a show on this machine is a note in
     * a file, and a feed will tell anybody who asks what came out this week.
     */
    var latest by mutableStateOf<List<Track>>(emptyList())
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
            val theChart = async {
                if (where == Where.Catalogue) emptyList()
                else Feeds.chart(limit = 14).map { it.asCard() }
            }
            // All at once. They are four unrelated questions to four different
            // parts of the catalogue, and asking them in turn would make the
            // page as slow as the sum of them rather than the slowest.
            val theFeed = async { Catalogue.showFeed() }
            val theFresh = async { Catalogue.freshEpisodes() }
            val theLater = async { Catalogue.episodesForLater() }
            val theChannels = async { Catalogue.showChannels() }
            chart = theChart.await()
            latest = newestFromFollowed()
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
            val fromCatalogue = async {
                if (where == Where.Directory) emptyList()
                else Catalogue.search(asked, Catalogue.Scope.Podcasts).getOrDefault(emptyList())
            }
            val fromDirectory = async {
                if (where == Where.Catalogue) emptyList()
                else Feeds.search(asked, limit = 16).map { it.asCard() }
            }
            val episodes = async {
                if (where == Where.Directory) emptyList()
                else Catalogue.search(asked, Catalogue.Scope.Episodes).getOrDefault(emptyList())
            }

            // The directory first where both have something, because that copy
            // can be seeked and this one cannot. Then deduped by name: The
            // Daily is on both, and one page listing it twice looks broken
            // rather than thorough.
            subjectShows = merge(fromDirectory.await(), fromCatalogue.await())
            subjectEpisodes = episodes.await()
        }
        loadingSubject = false
    }

    /**
     * One episode from each followed programme, newest first.
     *
     * One each rather than all of them: a daily show would otherwise fill the
     * row on its own and bury the weekly one somebody actually waits for. The
     * feeds are read together, since ten of them read in turn is ten times a
     * second of waiting for no reason.
     */
    private suspend fun newestFromFollowed(): List<Track> = coroutineScope {
        val followed = Library.saved.filter { Feeds.isFeed(it.id) }
        if (followed.isEmpty()) return@coroutineScope emptyList()
        followed
            .map { show -> async { Feeds.episodes(Feeds.feedOf(show.id), limit = 1).firstOrNull() } }
            .mapNotNull { it.await()?.asTrack() }
    }

    /**
     * Two lists of the same kind of thing, made one.
     *
     * Matched on the name with the noise taken out, since the same programme is
     * filed as "The Daily" in one place and "The Daily | The New York Times" in
     * the other, and neither is wrong.
     */
    private fun merge(first: List<Catalogue.Card>, second: List<Catalogue.Card>): List<Catalogue.Card> {
        val seen = mutableSetOf<String>()
        return (first + second).filter { seen.add(plainly(it.title)) }
    }

    private fun plainly(title: String) = title
        .lowercase()
        .substringBefore('|')
        .substringBefore('(')
        .replace(Regex("\\bpodcast\\b|\\bthe\\b|[^a-z0-9 ]"), "")
        .trim()
        .replace(Regex("\\s+"), " ")

    /** Show a different country's chart. */
    suspend fun chartFrom(code: String) {
        if (code == Feeds.country) return
        Feeds.chartFrom(code)
        chart = if (where == Where.Catalogue) emptyList()
        else Feeds.chart(limit = 14).map { it.asCard() }
    }

    /** Look somewhere else. */
    suspend fun lookIn(picked: Where) {
        if (picked == where) return
        where = picked
        runCatching { kept.writeText(picked.name) }
        forget()
        ensureLoaded()
    }

    /** Ask again — after signing in, when half of this becomes available. */
    fun forget() {
        loaded = false
        chart = emptyList()
        latest = emptyList()
        feed = emptyList()
        fresh = emptyList()
        later = emptyList()
        channels = emptyList()
    }
}
