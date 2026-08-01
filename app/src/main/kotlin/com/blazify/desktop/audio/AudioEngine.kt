package com.blazify.desktop.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Plays one stream at a time.
 *
 * The media toolkit has to be started once, on its own thread, before anything
 * can be constructed — and every call after that has to happen on that thread,
 * not the one Compose draws on. Both rules are handled here so nothing above
 * this file has to know they exist.
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

    private val started = AtomicBoolean(false)
    private var player: MediaPlayer? = null

    /**
     * Boots the media toolkit. Safe to call repeatedly: the flag wins the race,
     * and the toolkit itself throws if it's already up, which we swallow — that
     * outcome is exactly what we wanted anyway.
     */
    private fun ensureStarted() {
        if (started.compareAndSet(false, true)) {
            runCatching { Platform.startup { } }
            // Without this the toolkit shuts itself down the moment the last
            // window closes, taking playback with it. There is no window.
            runCatching { Platform.setImplicitExit(false) }
        }
    }

    /** Hand work to the media thread — everything below must run there. */
    private fun onFxThread(block: () -> Unit) {
        ensureStarted()
        Platform.runLater(block)
    }

    fun play(url: String) {
        error = null
        loading = true
        onFxThread {
            runCatching {
                player?.dispose()
                val media = Media(url)
                val fresh = MediaPlayer(media)

                fresh.setOnReady {
                    duration = fresh.media.duration?.toSeconds() ?: 0.0
                    loading = false
                    fresh.play()
                }
                fresh.setOnPlaying { playing = true }
                fresh.setOnPaused { playing = false }
                fresh.setOnStopped { playing = false }
                fresh.setOnEndOfMedia {
                    playing = false
                    position = duration
                }
                fresh.setOnError {
                    loading = false
                    playing = false
                    error = fresh.error?.message ?: "This track wouldn't play"
                }
                // Four times a second is enough for a progress bar and costs
                // nothing; the default is far chattier.
                fresh.currentTimeProperty().addListener { _, _, now ->
                    position = now.toSeconds()
                }

                player = fresh
            }.onFailure {
                loading = false
                error = it.message ?: "This track wouldn't play"
            }
        }
    }

    fun toggle() = onFxThread {
        val p = player ?: return@onFxThread
        if (p.status == MediaPlayer.Status.PLAYING) p.pause() else p.play()
    }

    fun seek(fraction: Double) = onFxThread {
        val p = player ?: return@onFxThread
        if (duration > 0) p.seek(Duration.seconds(fraction.coerceIn(0.0, 1.0) * duration))
    }

    fun setVolume(value: Double) = onFxThread {
        player?.volume = value.coerceIn(0.0, 1.0)
    }

    fun stop() = onFxThread {
        player?.stop()
        player?.dispose()
        player = null
        playing = false
        position = 0.0
        duration = 0.0
    }
}
