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
 * What the player does on its own.
 *
 * Every one of these is a decision somebody could reasonably make either way,
 * which is exactly why none of them is made here permanently. The defaults are
 * the quiet ones: keep playing rather than stop dead, don't fill anybody's disk
 * without being asked, and don't sit on a broken song waiting to be rescued.
 */
object Playback {

    private val store = File(Store.folder, "playback")

    /**
     * Whether the music carries on when the queue runs out.
     *
     * On, because silence at the end of an album is a decision the album made,
     * not one you made — and the alternative is noticing twenty minutes later
     * that nothing has been playing.
     */
    var keepGoing by mutableStateOf(true)
        private set

    /** Whether a song that won't play is skipped rather than stared at. */
    var skipBroken by mutableStateOf(true)
        private set

    /** Whether liking something also keeps a copy of it. */
    var keepWhatILike by mutableStateOf(false)
        private set

    /**
     * How long one song takes to give way to the next, in seconds.
     *
     * Zero means straight cuts, which is what an album that was sequenced wants
     * — a crossfade over a segue is a producer's work being talked over.
     */
    var fadeSeconds by mutableStateOf(0f)
        private set

    /** Whether the volume eases in when playback starts rather than arriving. */
    var easeIn by mutableStateOf(true)
        private set

    init {
        runCatching {
            if (!store.exists()) return@runCatching
            val lines = store.readLines()
            keepGoing = lines.getOrNull(0) != "false"
            skipBroken = lines.getOrNull(1) != "false"
            keepWhatILike = lines.getOrNull(2) == "true"
            fadeSeconds = lines.getOrNull(3)?.toFloatOrNull() ?: 0f
            easeIn = lines.getOrNull(4) != "false"
        }
    }

    private fun save() {
        runCatching {
            store.writeText(
                listOf(
                    keepGoing.toString(), skipBroken.toString(), keepWhatILike.toString(),
                    fadeSeconds.toString(), easeIn.toString(),
                ).joinToString("\n"),
            )
        }
    }

    fun chooseKeepGoing(value: Boolean) { keepGoing = value; save() }
    fun chooseSkipBroken(value: Boolean) { skipBroken = value; save() }
    fun chooseKeepWhatILike(value: Boolean) { keepWhatILike = value; save() }
    fun chooseEaseIn(value: Boolean) { easeIn = value; save() }

    fun chooseFade(value: Float) {
        fadeSeconds = value.coerceIn(0f, 8f)
        save()
    }

    fun reset() {
        keepGoing = true
        skipBroken = true
        keepWhatILike = false
        fadeSeconds = 0f
        easeIn = true
        save()
    }
}
