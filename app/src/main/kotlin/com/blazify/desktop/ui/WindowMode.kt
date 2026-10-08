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
 * it — the player bar and the small window's own way back — and neither can
 * see the other's state.
 */
object WindowMode {
    var mini by mutableStateOf(false)
        private set

    /**
     * Whether the big window is down in the taskbar.
     *
     * Asked by anything that moves on its own — a video playing behind the
     * player costs a decoder and a stream for as long as it runs, and nobody
     * is watching a window they have put away.
     */
    var minimised by mutableStateOf(false)
        private set

    fun noteMinimised(value: Boolean) { minimised = value }

    fun toggleMini() { mini = !mini }

    fun full() { mini = false }
}
