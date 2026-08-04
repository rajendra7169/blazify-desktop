package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Something looked for, and when. */
@Serializable
data class Searched(val words: String, val at: Long)

/**
 * What you have looked for before.
 *
 * People search for the same handful of things over and over — an artist they
 * are working through, a record they keep coming back to — and typing it out
 * again each time is the app forgetting something it watched you do an hour
 * ago. Kept here rather than on the account, because it is a record of what
 * was typed on this machine and belongs no further than that.
 */
object Searches {

    private const val FILE = "searches.json"

    /** Enough to cover a habit, few enough to stay a list rather than an archive. */
    private const val KEEP = 12

    var all by mutableStateOf(Store.read<Searched>(FILE).sortedByDescending { it.at })
        private set

    /**
     * Note something searched for.
     *
     * Only once it has been chosen — a query typed towards is not a query, and
     * recording every prefix on the way would fill this with the first three
     * letters of everything.
     */
    fun note(words: String) {
        val cleaned = words.trim()
        if (cleaned.length < 2) return
        all = (
            listOf(Searched(cleaned, System.currentTimeMillis())) +
                all.filterNot { it.words.equals(cleaned, ignoreCase = true) }
            ).take(KEEP)
        save()
    }

    fun forget(words: String) {
        all = all.filterNot { it.words == words }
        save()
    }

    fun forgetAll() {
        all = emptyList()
        save()
    }

    private fun save() = Store.write(FILE, all)
}
