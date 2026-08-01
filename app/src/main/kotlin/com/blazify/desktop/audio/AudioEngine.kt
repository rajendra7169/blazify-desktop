package com.blazify.desktop.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
            position = duration
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
            position = newTime / 1000.0
        }
    }

    /** Whether the native library could be found. False means VLC isn't installed. */
    fun available(): Boolean = runCatching { component; true }.getOrDefault(false)

    fun play(mrl: String) {
        error = null
        loading = true
        position = 0.0
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
    }

    fun seek(fraction: Double) {
        runCatching { player.controls().setPosition(fraction.coerceIn(0.0, 1.0).toFloat()) }
    }

    fun setVolume(value: Double) {
        runCatching { player.audio().setVolume((value.coerceIn(0.0, 1.0) * 100).toInt()) }
    }

    fun stop() {
        runCatching { player.controls().stop() }
        playing = false
        position = 0.0
        duration = 0.0
    }
}
