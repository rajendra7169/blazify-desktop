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
 * Bringing everything to about the same loudness.
 *
 * A record mastered in 1975 and one mastered last year are ten decibels apart,
 * and a queue that mixes them is a queue somebody rides with a hand on the
 * volume. This levels what is heard rather than trusting what a file claims,
 * because almost nothing played here carries a loudness figure at all.
 *
 * Off by default. It is a compressor, and a compressor is not free: it takes
 * the top off the loud moments, which is exactly what somebody listening to a
 * qawwali build for eight minutes does not want. The people who need it know
 * they need it.
 */
object Levelling {

    private val store: File get() = File(Store.folder, "levelling")

    var on by mutableStateOf(false)
        private set

    /**
     * How hard it is allowed to pull.
     *
     * Two is the player's own default and is gentle; higher evens out more and
     * flattens more with it.
     */
    var target by mutableStateOf(2.0f)
        private set

    init {
        runCatching {
            if (store.exists()) {
                val lines = store.readLines()
                on = lines.getOrNull(0) == "true"
                target = lines.getOrNull(1)?.toFloatOrNull() ?: 2.0f
            }
        }
    }

    fun choose(value: Boolean) {
        on = value
        save()
    }

    fun chooseTarget(value: Float) {
        target = value.coerceIn(1f, 5f)
        save()
    }

    private fun save() {
        runCatching { store.writeText("$on\n$target") }
    }
}
