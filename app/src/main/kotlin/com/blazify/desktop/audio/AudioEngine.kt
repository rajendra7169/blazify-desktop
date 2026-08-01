package com.blazify.desktop.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Plays one track at a time.
 *
 * Built on a native audio component rather than a JVM media library, for one
 * concrete reason: the catalogue serves fragmented MP4, and the lighter options
 * refuse to open it at all. This one takes it as it comes.
 *
 * Every callback arrives on a native thread, so nothing here touches anything
 * beyond the state fields — those are observable, and the UI reads them.
 */
object AudioEngine {

    var position by mutableStateOf(0.0)      // seconds
        private set
    var duration by mutableStateOf(0.0)      // seconds
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Called when a track reaches its end, so the queue can move on. */
    var onFinished: (() -> Unit)? = null

    /**
     * The player reports the time it has reached, but only every few hundred
     * milliseconds — enough to move a progress bar, nowhere near enough to
     * follow a line of a song. So the last report is kept with the moment it
     * arrived, and the time between reports is carried by the wall clock.
     *
     * The result is a position that moves continuously and is corrected by the
     * truth several times a second, rather than one that jumps.
     */
    private var anchor = 0.0
    private var anchoredAt = System.nanoTime()

    private fun anchorAt(seconds: Double) {
        anchor = seconds
        anchoredAt = System.nanoTime()
        position = seconds
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (true) {
                delay(40)
                if (!playing) continue
                val since = (System.nanoTime() - anchoredAt) / 1_000_000_000.0
                val guess = anchor + since
                position = if (duration > 0) guess.coerceAtMost(duration) else guess
            }
        }
    }

    /**
     * Created lazily. Building it loads the native library, and doing that at
     * startup would slow the window down for someone who never presses play.
     */
    private val component: AudioPlayerComponent by lazy {
        AudioPlayerComponent().also { it.mediaPlayer().events().addMediaPlayerEventListener(Listener) }
    }

    private val player: MediaPlayer get() = component.mediaPlayer()

    private object Listener : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) {
            playing = true
            loading = false
        }

        override fun paused(mediaPlayer: MediaPlayer) { playing = false }

        override fun stopped(mediaPlayer: MediaPlayer) {
            playing = false
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            playing = false
            anchorAt(duration)
            // The callback arrives on a native thread and starting the next
            // track from inside it deadlocks the player, so hand it off.
            Thread { onFinished?.invoke() }.start()
        }

        override fun error(mediaPlayer: MediaPlayer) {
            loading = false
            playing = false
            error = "This track wouldn't play"
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            if (newLength > 0) duration = newLength / 1000.0
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            anchorAt(newTime / 1000.0)
        }
    }

    /** Whether the native library could be found. False means VLC isn't installed. */
    fun available(): Boolean = runCatching { component; true }.getOrDefault(false)

    fun play(mrl: String) {
        error = null
        loading = true
        anchorAt(0.0)
        duration = 0.0
        runCatching { player.media().play(mrl) }
            .onFailure {
                loading = false
                error = it.message ?: "This track wouldn't play"
            }
    }

    fun toggle() {
        runCatching {
            if (player.status().isPlaying) player.controls().pause() else player.controls().play()
        }
        // Resuming counts on from here, not from whenever the last report was.
        anchorAt(position)
    }

    fun seek(fraction: Double) {
        val target = fraction.coerceIn(0.0, 1.0)
        runCatching { player.controls().setPosition(target.toFloat()) }
        // Moved now rather than waiting for the player to say so, or the
        // in-between would be spent counting up from where you just left.
        if (duration > 0) anchorAt(target * duration)
    }

    fun setVolume(value: Double) {
        runCatching { player.audio().setVolume((value.coerceIn(0.0, 1.0) * 100).toInt()) }
    }

    fun stop() {
        runCatching { player.controls().stop() }
        playing = false
        anchorAt(0.0)
        duration = 0.0
    }
}
