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
 * Saying what came on, where the desktop puts such things.
 *
 * The window is usually behind something else — that is the whole point of a
 * music player — so the one moment worth interrupting for is the moment the
 * song changes, when the question "what is this" has an answer nobody can see.
 *
 * Told through the desktop's own notification service rather than drawn here:
 * it already knows where notifications belong on this screen, whether the
 * person is presenting, and whether they have asked for quiet. A window
 * painting its own toast in the corner knows none of that.
 */
object Notify {

    var on by mutableStateOf(true)
        private set

    private val settings: File get() = File(Store.folder, "notify")

    init {
        runCatching { if (settings.exists()) on = settings.readText().trim() != "false" }
    }

    fun choose(value: Boolean) {
        on = value
        runCatching { settings.writeText(on.toString()) }
    }

    /** Whether this desktop has anywhere to say it. */
    val available: Boolean by lazy {
        runCatching {
            ProcessBuilder("which", "notify-send").start().waitFor() == 0
        }.getOrDefault(false)
    }

    private var lastSaid: String? = null

    /**
     * Say what is playing, once per song.
     *
     * Guarded against repeating itself: the same song pausing and resuming is
     * not news, and a notification per pause would make the corner of somebody's
     * screen flicker every time they answered a message.
     */
    fun nowPlaying(track: Track?) {
        if (!on || !available) return
        val playing = track ?: return
        if (lastSaid == playing.id) return
        lastSaid = playing.id

        runCatching {
            val command = mutableListOf(
                "notify-send",
                "--app-name=Blazify",
                // Replaces the previous one rather than stacking. Twenty songs
                // into an evening, twenty notifications is a wall.
                "--hint=string:x-canonical-private-synchronous:blazify",
                "--expire-time=4000",
            )
            // The cover, when it is already on this machine. Downloading one to
            // decorate a notification would be spending the network on
            // something that will be gone in four seconds.
            Offline.artFor(playing.id).takeIf { it.exists() && it.length() > 0 }
                ?.let { command += "--icon=${it.absolutePath}" }
            command += playing.title
            command += playing.artist
            ProcessBuilder(command).start()
        }
    }
}
