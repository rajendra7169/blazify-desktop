package com.blazify.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everything you can do to a song, on right-click.
 *
 * The same menu wherever a song appears — a shelf, a search result, a playlist,
 * the queue — because "what can I do with this" shouldn't depend on which list
 * you happened to find it in. Wrapping is deliberate: the row keeps its own
 * click behaviour, and this only adds the second button.
 *
 * The wording answers with the current state rather than offering both sides,
 * so there's one item to read instead of two to choose between.
 */
@Composable
fun SongMenu(track: Track, content: @Composable () -> Unit) {
    ContextMenuArea(
        items = {
            buildList {
                add(ContextMenuItem("Play next") { PlayerState.playNext(track) })
                add(ContextMenuItem("Add to queue") { PlayerState.addToQueue(track) })
                add(ContextMenuItem("Add to playlist…") { Dialogs.addToPlaylist(track) })
                add(
                    ContextMenuItem(
                        if (Library.isLiked(track.id)) "Remove from liked songs" else "Like",
                    ) { Library.toggleLike(track) },
                )
                // A file already on the machine has nowhere to be downloaded to.
                if (!LocalMusic.isLocal(track.id)) {
                    add(
                        if (Downloads.has(track.id)) {
                            ContextMenuItem("Remove download") { Downloads.remove(track.id) }
                        } else {
                            ContextMenuItem("Keep for offline") { Downloads.start(track) }
                        },
                    )
                }
            }
        },
        content = content,
    )
}
