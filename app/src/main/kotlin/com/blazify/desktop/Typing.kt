package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether a text field currently has the keyboard.
 *
 * The single-letter shortcuts stand down while it does. Without this, searching
 * for "space oddity" would pause the music at the first word and skip a track
 * at the last — the keys have to lose to whatever you're typing into.
 */
object Typing {
    var active by mutableStateOf(false)
}
