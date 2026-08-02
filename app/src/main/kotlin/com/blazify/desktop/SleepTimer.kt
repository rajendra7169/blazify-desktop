package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Stop the music after a while.
 *
 * Two ways to say when: a number of minutes, or "when this song ends" — which
 * is the one people actually reach for at the point of falling asleep, and the
 * one a plain countdown can't express.
 *
 * It pauses rather than quits. Waking up to a closed application and a lost
 * queue is a worse outcome than waking up to a paused one.
 */
object SleepTimer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Seconds left, or null when nothing is set. */
    var remaining by mutableStateOf<Int?>(null)
        private set

    /** Set to stop at the end of the track rather than at a time. */
    var atEndOfTrack by mutableStateOf(false)
        private set

    val running: Boolean get() = remaining != null || atEndOfTrack

    val readout: String
        get() = when {
            atEndOfTrack -> "End of song"
            else -> remaining?.let { "%d:%02d".format(it / 60, it % 60) } ?: "Off"
        }

    fun start(minutes: Int) {
        cancel()
        remaining = minutes * 60
        job = scope.launch {
            while (isActive) {
                delay(1000)
                val left = (remaining ?: return@launch) - 1
                if (left <= 0) {
                    stopPlayback()
                    return@launch
                }
                remaining = left
            }
        }
    }

    fun endOfTrack() {
        cancel()
        atEndOfTrack = true
    }

    fun cancel() {
        job?.cancel()
        job = null
        remaining = null
        atEndOfTrack = false
    }

    /**
     * Called when a track finishes, before the next one starts.
     *
     * Returns whether it swallowed the advance — the queue must not move on if
     * the whole point was to stop here.
     */
    fun consumeTrackEnd(): Boolean {
        if (!atEndOfTrack) return false
        cancel()
        return true
    }

    private fun stopPlayback() {
        cancel()
        if (PlayerState.playing) PlayerState.toggle()
    }
}
