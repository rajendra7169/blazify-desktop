package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.LocalDate

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Asking for a star, at most three times, and then never again.
 *
 * The rules matter more than the feature. A prompt that keeps returning is how
 * a program earns a bad word from somebody who actually liked it, so:
 *
 * - Days are counted by opening the application, not by the calendar since it
 *   was installed. Somebody who installed it and forgot has not used it for
 *   three days.
 * - Later means later, and the gap grows: three days, then a fortnight, then a
 *   month, after which it stops on its own.
 * - No thanks means never, with no route back into the schedule.
 * - It is never shown while something is playing, which the caller enforces,
 *   because interrupting music to ask a favour is worse than not asking.
 *
 * The link in About stays regardless, for anyone who says no and changes their
 * mind.
 */
object StarPrompt {
    const val REPO = "https://github.com/rajendra7169/blazify-desktop"

    private const val DAYS_BEFORE_FIRST_ASK = 3
    private val LATER_GAPS = listOf(15L, 30L)
    private const val MAX_ASKS = 3

    private val store = File(Store.folder, "star")

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
     * Records that the application was opened today, and shows the prompt if it
     * is due. Safe to call on every launch: the day counter moves at most once
     * per calendar day.
     */
    fun onOpened(somethingIsPlaying: Boolean) {
        if (done || somethingIsPlaying) return

        val today = LocalDate.now().toString()
        if (lastDay != today) {
            lastDay = today
            daysUsed += 1
            write()
        }

        if (daysUsed < DAYS_BEFORE_FIRST_ASK) return
        if (asks >= MAX_ASKS) return
        if (System.currentTimeMillis() < nextAt) return

        asks += 1
        // The next gap is longer, and after the last one there is no next.
        val gap = LATER_GAPS.getOrNull(asks - 1)
        if (gap == null) done = true else nextAt = System.currentTimeMillis() + gap * 86_400_000L
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
        runCatching {
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)
            if (windows) {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", REPO).start()
            } else {
                ProcessBuilder("xdg-open", REPO).start()
            }
        }
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
