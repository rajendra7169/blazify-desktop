package com.blazify.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.blazify.desktop.ui.Theatre

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The keys a music player is expected to answer to.
 *
 * Split by what the key means rather than handled all one way. Volume and
 * seeking are things you do *by degree*: holding the key should keep going, so
 * they act on press and repeat as long as it's held. Play, next and mute are
 * things you do *once*, so they act on release — a held space bar toggling play
 * thirty times a second is not what anyone meant by it.
 *
 * Returns whether the key was ours. Anything we don't claim carries on to
 * whatever had focus, so typing a search query still types.
 */
object Shortcuts {

    /** How far the arrow keys move through a track. */
    private const val STEP = 5.0

    /**
     * How much one press changes the volume.
     *
     * Ten presses covers the whole range. Any finer and getting from loud to
     * quiet is a drum solo on the arrow key.
     */
    private const val VOLUME_STEP = 0.1f

    fun handle(event: KeyEvent, typing: Boolean): Boolean {
        val pressed = event.type == KeyEventType.KeyDown
        val released = event.type == KeyEventType.KeyUp
        if (!pressed && !released) return false

        // The media keys stay live everywhere. The letter and space shortcuts
        // stand down while there's a text field in play — otherwise a search
        // for "space oddity" would pause the music halfway through typing it.
        if (released) {
            when (event.key) {
                Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { PlayerState.toggle(); return true }
                Key.MediaNext -> { PlayerState.next(); return true }
                Key.MediaPrevious -> { PlayerState.previous(); return true }
                Key.MediaStop -> { PlayerState.toggle(); return true }
                else -> Unit
            }
        }
        // Out of the full-screen sheet, before anything else looks at the key.
        // Escape means "put the window back" everywhere else on a desktop, and
        // it works while typing because the search box in a covered window
        // isn't the thing being escaped from.
        if (released && event.key == Key.Escape && Theatre.leave()) return true

        if (typing) return false

        // By degree — held down, these keep going.
        if (pressed) {
            when (event.key) {
                Key.DirectionUp -> { PlayerState.changeVolume(PlayerState.volume + VOLUME_STEP); return true }
                Key.DirectionDown -> { PlayerState.changeVolume(PlayerState.volume - VOLUME_STEP); return true }
                Key.DirectionRight, Key.L -> { PlayerState.nudge(STEP); return true }
                Key.DirectionLeft, Key.J -> { PlayerState.nudge(-STEP); return true }
                else -> Unit
            }
        }

        // Once each — and the matching release is swallowed so the key doesn't
        // reach anything behind us.
        return when (event.key) {
            Key.Spacebar, Key.K -> { if (released) PlayerState.toggle(); true }
            Key.N -> { if (released) PlayerState.next(); true }
            Key.P -> { if (released) PlayerState.previous(); true }
            Key.M -> { if (released) PlayerState.toggleMute(); true }
            Key.DirectionUp, Key.DirectionDown, Key.DirectionRight, Key.DirectionLeft, Key.J -> true
            else -> false
        }
    }
}
