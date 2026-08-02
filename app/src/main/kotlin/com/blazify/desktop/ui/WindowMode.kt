package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Which window is showing.
 *
 * Held here rather than inside the window itself because three places ask for
 * it — the tray menu, the player bar, and the small window's own way back —
 * and none of them can see the others' state.
 */
object WindowMode {
    var mini by mutableStateOf(false)
        private set

    fun toggleMini() { mini = !mini }

    fun full() { mini = false }
}
