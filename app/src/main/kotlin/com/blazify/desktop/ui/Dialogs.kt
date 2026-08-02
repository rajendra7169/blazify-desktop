package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Dialogs asked for from anywhere.
 *
 * A song row deep inside a shelf has no way to reach the top of the window,
 * and threading a callback down through every list to every row so one menu
 * item can open one dialog is a lot of wiring for a small thing. The request
 * is left here instead, and the shell picks it up.
 */
object Dialogs {
    /** The song waiting to be put in a playlist, if any. */
    var addingTo by mutableStateOf<Track?>(null)
        private set

    fun addToPlaylist(track: Track) { addingTo = track }

    fun dismiss() { addingTo = null }
}
