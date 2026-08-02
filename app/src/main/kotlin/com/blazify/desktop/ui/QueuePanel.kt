package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.blazify.desktop.PlayerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What's coming, down the right-hand side.
 *
 * A panel rather than a sheet: on a wide screen there's room to keep it open
 * while you carry on browsing, which is the whole reason to have a desktop
 * version. It scrolls to whatever is playing when you open it — with a hundred
 * tracks loaded, opening at the top would be useless.
 */
@Composable
fun QueuePanel(
    queue: List<Track>,
    current: Int,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(current, queue.size) {
        if (current in queue.indices) {
            listState.scrollToItem(current.coerceAtLeast(0))
        }
    }

    Column(
        modifier
            // Wider than a sidebar wants to be, because a queue row carries a
            // cover, two lines of text and a length — at 274dp every title was
            // an ellipsis and the list read as a column of half-names.
            .width(348.dp)
            .fillMaxHeight()
            .background(Blz.rail)
            .padding(start = 14.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Playing from", color = Blz.dim, fontSize = 11.sp)
                Text(
                    PlayerState.playingFrom ?: "Your queue",
                    color = Blz.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            // A queue is the one list nobody builds on purpose — it collects
            // out of what you played, what you added and where a radio went,
            // and it is gone the moment you play something else. Keeping it is
            // the difference between an evening you can play again and one you
            // can only remember.
            if (queue.isNotEmpty()) {
                val (source, hovered) = rememberHovered()
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Blz.surfaceHigh)
                        .hoverBackground(Blz.hover, hovered, source)
                        .clickable { Dialogs.keepQueue() }
                        .padding(start = 13.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(16.dp), tint = Blz.ink)
                    Text("Save", color = Blz.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (queue.isEmpty()) {
            Text("Nothing queued yet", color = Blz.dim, fontSize = 12.sp)
            return@Column
        }

        // No scrollbar drawn beside it. The wheel scrolls it, the current song
        // scrolls itself into view, and a permanent grey stripe down the edge
        // of a 350dp panel is a third of a column spent saying "this is a
        // list", which the list already says.
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            itemsIndexed(queue, key = { i, t -> "$i-${t.id}" }) { i, track ->
                QueueRow(
                    track = track,
                    playing = i == current,
                    upcoming = i > current,
                    onJump = { onJump(i) },
                    onRemove = { onRemove(i) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    playing: Boolean,
    upcoming: Boolean,
    onJump: () -> Unit,
    onRemove: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (playing) Modifier.background(Blz.surfaceHigh) else Modifier)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onJump)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // The cover, or a play mark for the one that's on. Swapped rather than
        // shown alongside: the row that is playing needs to be findable in a
        // glance down the list, and a second small square beside forty others
        // is not what finds it.
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                Icon(
                    Icons.Rounded.PlayArrow, "Playing",
                    Modifier.size(22.dp), tint = Blaze.Amber,
                )
            } else {
                Artwork(track.thumbnail, size = 38.dp, corner = 5.dp)
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.title,
                // Played tracks stay visible but recede, so the line between
                // done and coming is readable at a glance.
                color = if (playing) Blaze.Amber else if (upcoming) Blz.ink else Blz.dim,
                fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        // The length, until the pointer arrives — then the way to drop it.
        // One column, so nothing shifts sideways when a row is hovered.
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (hovered.value) {
                Icon(
                    Icons.Rounded.Close, "Remove",
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onRemove)
                        .padding(4.dp),
                    tint = Blz.ink,
                )
            } else {
                Text(track.duration, color = Blz.dim, fontSize = 11.5.sp)
            }
        }
    }
}
