package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.EmptyState
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Songs kept on this machine.
 *
 * The header carries the one number that matters for a folder of audio — how
 * much room it's taking — because that's the question anyone opening this
 * screen came to answer.
 */
@Composable
fun DownloadsScreen(onPlay: (List<Track>, Int) -> Unit, onShuffle: (List<Track>) -> Unit) {
    val kept = Downloads.items

    if (kept.isEmpty()) {
        EmptyState(
            Icons.Rounded.Download,
            "Nothing kept yet",
            "Keep a song and it plays without the network — right-click any song, " +
                "or use the arrow beside what's playing.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Downloads", color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (kept.isEmpty()) "Keep a song and it plays without the network"
                            else "${kept.size} songs  ·  ${size(Downloads.bytes)}",
                            color = Blz.muted, fontSize = 13.sp,
                        )
                    }
                    if (kept.isNotEmpty()) TextAction("Remove all", Downloads::removeAll)
                }

                if (kept.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(Icons.Rounded.PlayArrow, "Play", filled = true) { onPlay(kept, 0) }
                        Pill(Icons.Rounded.Shuffle, "Shuffle", filled = false) { onShuffle(kept) }
                    }
                }

                Downloads.failure?.let {
                    Text(it, color = Blz.muted, fontSize = 12.5.sp)
                }
            }
        }

        itemsIndexed(kept, key = { at, track -> "$at-${track.id}" }) { at, track ->
            KeptRow(track) { onPlay(kept, at) }
        }
    }
}

@Composable
private fun KeptRow(track: Track, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    val fetching = Downloads.isRunning(track.id)

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .hoverBackground(Blz.hover, hovered, source)
                .clickable(enabled = !fetching, onClick = onPlay)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Artwork(track.thumbnail, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    track.title, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist, color = Blz.muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (track.duration.isNotEmpty()) {
                Text(track.duration, color = Blz.dim, fontSize = 12.sp)
            }
            val (removeSource, removeHovered) = rememberHovered()
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .hoverBackground(Blz.hover, removeHovered, removeSource)
                    .clickable { Downloads.remove(track.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Remove", Modifier.size(15.dp), tint = Blz.dim)
            }
        }
        if (fetching) {
            LinearProgressIndicator(
                progress = { Downloads.progressOf(track.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                color = Blaze.Amber,
                trackColor = Blz.surfaceHigh,
            )
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surface),
            )
            .then(
                if (filled) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val ink = if (filled) Blaze.OnAmber else Blz.ink
        Icon(icon, label, Modifier.size(19.dp), tint = ink)
        Text(label, color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = Blz.muted, fontSize = 12.5.sp)
    }
}

/** Bytes, in the unit a person would have said it in. */
private fun size(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "%.0f KB".format(bytes / 1000.0)
}
