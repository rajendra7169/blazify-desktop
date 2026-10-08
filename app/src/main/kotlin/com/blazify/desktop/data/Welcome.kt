package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The few pages somebody sees the first time Blazify opens, and never again.
 *
 * Finishing and skipping are the same decision as far as this is concerned:
 * either way the person has said they have seen enough, and a welcome that
 * comes back is no longer a welcome. Settings can bring it back on purpose.
 *
 * Somebody who already has a history from an earlier version is not new, so
 * they are marked as welcomed without being shown anything — the pages are
 * for people who have not played a song here yet.
 */
object Welcome {

    private val marker = File(Store.folder, "welcomed")

    /** True while the pages should be on screen. */
    var showing by mutableStateOf(false)
        private set

    init {
        when {
            marker.exists() -> Unit
            File(Store.folder, "history.json").exists() || File(Store.folder, "plays.json").exists() ->
                remember()
            else -> showing = true
        }
    }

    /** Finished or skipped: either way, not again. */
    fun done() {
        showing = false
        remember()
    }

    /** Asked for from Settings. Doesn't change what happens on the next launch. */
    fun showAgain() {
        showing = true
    }

    private fun remember() {
        runCatching { marker.writeText("1") }
    }
}
