package com.blazify.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Lyrics
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The things you can do with a song's words, opened from the lyrics menu.
 *
 * Held here rather than inside the panel because they are dialogs: they cover
 * the whole window, and the panel is a strip down one side of it. The menu
 * says which one is open; the window draws it above everything else.
 */
object LyricsTools {
    /** A song whose lines are being made into a picture. */
    var sharing by mutableStateOf<Pair<Track, Lyrics>?>(null)

    /** A song whose plain words are being timed by hand. */
    var syncing by mutableStateOf<Pair<Track, Lyrics>?>(null)

    fun share(track: Track, lyrics: Lyrics) {
        sharing = track to lyrics
    }

    fun sync(track: Track, lyrics: Lyrics) {
        syncing = track to lyrics
    }
}

/** Whichever of the lyrics dialogs is open, over the whole window. */
@Composable
fun LyricsToolDialogs() {
    LyricsTools.sharing?.let { (track, lyrics) ->
        ShareLyricsDialog(track, lyrics, onDismiss = { LyricsTools.sharing = null })
    }
}
