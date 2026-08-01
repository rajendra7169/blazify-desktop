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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            .width(274.dp)
            .fillMaxHeight()
            .background(Blz.rail)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", color = Blz.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.weight(1f))
            Text(
                when (queue.size) {
                    0 -> ""
                    1 -> "1 song"
                    else -> "${queue.size} songs"
                },
                color = Blz.dim, fontSize = 11.sp,
            )
        }

        if (queue.isEmpty()) {
            Text("Nothing queued yet", color = Blz.dim, fontSize = 12.sp)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), state = listState) {
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
            .clip(RoundedCornerShape(7.dp))
            .then(if (playing) Modifier.background(Blz.surfaceHigh) else Modifier)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onJump)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(track.thumbnail, size = 34.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                // Played tracks stay visible but recede, so the line between
                // done and coming is readable at a glance.
                color = if (playing) Blaze.Amber else if (upcoming) Blz.ink else Blz.dim,
                fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Blz.muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (hovered.value) {
            Icon(
                Icons.Rounded.Close, "Remove",
                Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onRemove),
                tint = Blz.muted,
            )
        }
    }
}
