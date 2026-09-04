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
 * Asking for a star: once a day after the first launch, then weekly for two
 * months, and then never again.
 *
 * The rules matter more than the feature. A prompt that keeps returning is how
 * a program earns a bad word from somebody who actually liked it, so:
 *
 * - The clock starts on first launch and the first ask is a day later, so a
 *   program opened once and abandoned never asks at all.
 * - Later means later: a week each time, and after the last of them it stops
 *   on its own rather than carrying on.
 * - No thanks means never, with no route back into the schedule.
 * - It is never shown while something is playing, which the caller enforces,
 *   because interrupting music to ask a favour is worse than not asking.
 *
 * The link in About stays regardless, for anyone who says no and changes their
 * mind.
 */
object StarPrompt {
    const val REPO = "https://github.com/rajendra7169/blazify-desktop"

    private const val HOURS_BEFORE_FIRST_ASK = 24L
    private const val DAYS_BETWEEN_ASKS = 7L

    /** The first ask a day in, then one a week: the last lands just short of two months. */
    private const val MAX_ASKS = 9

    private val store = File(Store.folder, "star")

    // Fields 1 and 2 of the store file, kept so a file written by an older
    // build still parses. The schedule no longer counts days of use.
    private var daysUsed = 0
    private var lastDay = ""
    private var nextAt = 0L
    private var asks = 0
    private var done = false

    /** True while the dialog should be on screen. */
    var showing by mutableStateOf(false)
        private set

    init {
        read()
    }

    /**
     * Records the launch and shows the prompt if it is due. Safe to call on
     * every launch: the schedule only moves when an ask is actually shown.
     */
    fun onOpened(somethingIsPlaying: Boolean) {
        if (done || somethingIsPlaying) return
        if (asks >= MAX_ASKS) return

        if (nextAt == 0L) {
            // First launch. Start the clock and ask nothing yet — a program
            // that begs for a star before it has played a song has not earned
            // one.
            nextAt = System.currentTimeMillis() + HOURS_BEFORE_FIRST_ASK * 3_600_000L
            write()
            return
        }
        if (System.currentTimeMillis() < nextAt) return

        asks += 1
        // A week until the next, and after the last one there is no next.
        if (asks >= MAX_ASKS) {
            done = true
        } else {
            nextAt = System.currentTimeMillis() + DAYS_BETWEEN_ASKS * 86_400_000L
        }
        write()
        showing = true
    }

    /** Later. It will come back once, further away, and then not at all. */
    fun dismiss() {
        showing = false
    }

    /** Starred, or declined. Either way there is nothing left to ask. */
    fun stop() {
        showing = false
        done = true
        write()
    }

    fun open() {
        stop()
        Browse.open(REPO)
    }

    private fun read() {
        runCatching {
            val parts = store.readText().trim().split('\n')
            daysUsed = parts.getOrNull(0)?.toIntOrNull() ?: 0
            lastDay = parts.getOrNull(1).orEmpty()
            nextAt = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            asks = parts.getOrNull(3)?.toIntOrNull() ?: 0
            done = parts.getOrNull(4) == "true"
        }
    }

    private fun write() {
        runCatching { store.writeText("$daysUsed\n$lastDay\n$nextAt\n$asks\n$done") }
    }
}
