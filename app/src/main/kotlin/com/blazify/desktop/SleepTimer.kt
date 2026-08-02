package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.audio.AudioEngine
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
 * Three ways to say when, because they answer different questions. A number of
 * minutes is for "I'll be asleep by then". The end of this song is for "not
 * mid-chorus". A number of songs is for "a few more" — which is how people
 * actually think at that point, and which no clock can express.
 *
 * It pauses rather than quits. Waking up to a closed application and a lost
 * queue is a worse outcome than waking up to a paused one.
 */
object SleepTimer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** What the timer is counting, if anything. */
    enum class Mode { Off, Clock, EndOfTrack, Songs }

    var mode by mutableStateOf(Mode.Off)
        private set

    /** Seconds left on the clock. */
    var remaining by mutableStateOf(0)
        private set

    /** Songs left to play, including the one playing now. */
    var songsLeft by mutableStateOf(0)
        private set

    /** How long the clock was set for, so the bar knows how far along it is. */
    private var total = 0

    /**
     * Whether to fade out rather than stop dead.
     *
     * Falling asleep to music that cuts off is a way of being woken by the
     * silence; a fade is the difference between the music ending and the music
     * stopping.
     */
    var fade by mutableStateOf(true)
        private set

    fun chooseFade(value: Boolean) { fade = value }

    val running: Boolean get() = mode != Mode.Off

    /** How much of the clock is left, 0..1, for drawing a bar. */
    val progress: Float
        get() = if (mode == Mode.Clock && total > 0) remaining.toFloat() / total else 0f

    val readout: String
        get() = when (mode) {
            Mode.Off -> "Off"
            Mode.EndOfTrack -> "End of song"
            Mode.Songs -> if (songsLeft == 1) "1 song" else "$songsLeft songs"
            Mode.Clock -> "%d:%02d".format(remaining / 60, remaining % 60)
        }

    fun startClock(minutes: Int) {
        cancel()
        mode = Mode.Clock
        total = minutes * 60
        remaining = total
        job = scope.launch {
            while (isActive) {
                delay(1000)
                val left = remaining - 1
                if (left <= 0) {
                    finish()
                    return@launch
                }
                remaining = left
            }
        }
    }

    fun endOfTrack() {
        cancel()
        mode = Mode.EndOfTrack
    }

    fun afterSongs(count: Int) {
        cancel()
        mode = Mode.Songs
        songsLeft = count.coerceAtLeast(1)
    }

    fun cancel() {
        job?.cancel()
        job = null
        mode = Mode.Off
        remaining = 0
        songsLeft = 0
        total = 0
    }

    /**
     * Called when a track finishes, before the next one starts.
     *
     * Returns whether it swallowed the advance — the queue must not move on if
     * the whole point was to stop here.
     */
    fun consumeTrackEnd(): Boolean = when (mode) {
        Mode.EndOfTrack -> {
            cancel()
            true
        }
        Mode.Songs -> {
            songsLeft -= 1
            if (songsLeft <= 0) {
                cancel()
                true
            } else {
                false
            }
        }
        else -> false
    }

    /**
     * Bring it to a stop.
     *
     * The volume is put back afterwards, or the next thing played would start
     * silent and look broken.
     */
    private suspend fun finish() {
        val was = PlayerState.volume
        if (fade) {
            val steps = 20
            repeat(steps) { step ->
                PlayerState.changeVolume(was * (1f - (step + 1f) / steps))
                delay(150)
            }
        }
        cancel()
        if (AudioEngine.playing) PlayerState.toggle()
        PlayerState.changeVolume(was)
    }
}
