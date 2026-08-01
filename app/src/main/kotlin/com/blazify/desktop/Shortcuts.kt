package com.blazify.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The keys a music player is expected to answer to.
 *
 * Handled on release rather than press: a held key repeats, and a held space
 * bar toggling play thirty times a second is not what anyone meant by it.
 *
 * Returns whether the key was ours. Anything we don't claim carries on to
 * whatever had focus, so typing a search query still types.
 */
object Shortcuts {

    /** How far the arrow keys move through a track. */
    private const val STEP = 5.0

    /** How much one press changes the volume. */
    private const val VOLUME_STEP = 0.05f

    fun handle(event: KeyEvent, typing: Boolean): Boolean {
        if (event.type != KeyEventType.KeyUp) return false

        // The media keys stay live everywhere. The letter and space shortcuts
        // stand down while there's a text field in play — otherwise a search
        // for "space oddity" would pause the music halfway through typing it.
        val media = when (event.key) {
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { PlayerState.toggle(); true }
            Key.MediaNext -> { PlayerState.next(); true }
            Key.MediaPrevious -> { PlayerState.previous(); true }
            Key.MediaStop -> { PlayerState.toggle(); true }
            else -> false
        }
        if (media || typing) return media

        return when (event.key) {
            Key.Spacebar, Key.K -> { PlayerState.toggle(); true }
            Key.DirectionRight, Key.L -> { PlayerState.nudge(STEP); true }
            Key.DirectionLeft, Key.J -> { PlayerState.nudge(-STEP); true }
            Key.N -> { PlayerState.next(); true }
            Key.P -> { PlayerState.previous(); true }
            Key.DirectionUp -> { PlayerState.changeVolume(PlayerState.volume + VOLUME_STEP); true }
            Key.DirectionDown -> { PlayerState.changeVolume(PlayerState.volume - VOLUME_STEP); true }
            Key.M -> { PlayerState.toggleMute(); true }
            else -> false
        }
    }
}
