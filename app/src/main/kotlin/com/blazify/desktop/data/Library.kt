package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True while the account's own likes are being fetched. */
    var syncing by mutableStateOf(false)
        private set

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

    /**
     * Like or unlike a song.
     *
     * The list here changes first and the account is told afterwards. A heart
     * that waits for the network before filling in feels broken on a slow
     * connection, and the answer is never in doubt — the only thing that can
     * go wrong is the account not hearing about it, which the next sync fixes.
     */
    fun toggleLike(track: Track) {
        val wanted = !isLiked(track.id)
        liked = if (wanted) listOf(track) + liked else liked.filterNot { it.id == track.id }
        Store.write(LIKED, liked)
        scope.launch { Catalogue.setLiked(track.id, wanted) }
        Scrobbler.love(track, wanted)
        // Liking something can mean keeping it, if that was asked for. Only on
        // the way in — unliking should not throw away a copy you may still want.
        if (wanted && Playback.keepWhatILike && !Downloads.has(track.id)) Downloads.start(track)
    }

    /**
     * Bring the account's likes across.
     *
     * The account is taken as the truth for what it holds, and anything liked
     * here while signed out is pushed up rather than dropped — so the two
     * converge instead of one quietly winning.
     */
    fun syncWithAccount() {
        if (!Account.signedIn || syncing) return
        syncing = true
        scope.launch {
            Catalogue.myLikedSongs().onSuccess { theirs ->
                val ids = theirs.map { it.id }.toSet()
                val onlyHere = liked.filterNot { it.id in ids }

                liked = theirs + onlyHere
                Store.write(LIKED, liked)

                // Push what this machine knew and the account didn't.
                onlyHere.forEach { Catalogue.setLiked(it.id, true) }
            }
            syncing = false
        }
    }

    /**
     * Note that something was played.
     *
     * A song you return to shouldn't appear five times — the old entry is
     * dropped and it moves back to the top, so the list reads as "when did I
     * last hear this" rather than a tally.
     */
    fun played(track: Track) {
        // Counted every time, even when the history doesn't move — playing the
        // same song four times in a row is four plays, and it is exactly the
        // case a "most played" list exists to catch.
        Plays.note(track)
        if (history.firstOrNull()?.id == track.id) return
        history = (listOf(track) + history.filterNot { it.id == track.id }).take(HISTORY_LIMIT)
        Store.write(HISTORY, history)
    }

    fun clearHistory() {
        history = emptyList()
        Store.write(HISTORY, history)
        // The counts are the history counted. Clearing one and keeping the
        // other would leave a top list built from songs you can no longer see.
        Plays.forget()
    }

    /**
     * Everything ever seen, for the counts to be matched against.
     *
     * A play is stored as an id, so turning one back into a song means finding
     * it somewhere — the history holds most of them, and what you liked or
     * kept covers nearly all of the rest.
     */
    fun known(): List<Track> =
        (history + liked + Downloads.items + LocalMusic.tracks).distinctBy { it.id }

    fun isSaved(id: String) = saved.any { it.id == id }

    fun toggleSaved(card: Catalogue.Card) {
        saved = if (isSaved(card.id)) saved.filterNot { it.id == card.id }
        else listOf(card) + saved
        Store.write(SAVED, saved)
    }
    /**
     * Read the files in now, before anything draws.
     *
     * These lists are created the first time anything touches this object. If
     * that first touch happens inside a composition — which it does the moment
     * a restored queue gives the player a song to show a heart for — the state
     * is newer than the snapshot reading it, and Compose refuses the read.
     * Touching them during startup puts them safely before the first frame.
     */
    fun warm() {
        liked.size
        history.size
        saved.size
    }

}
