package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
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
import com.blazify.desktop.data.Cache
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.EmptyState
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.SongSheetButton
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What is here without anybody having asked for it.
 *
 * Shown rather than hidden, because a player quietly holding a gigabyte of
 * somebody's disk should say so, and because the list answers the question
 * people actually have when the connection dies: what can I still play.
 *
 * Kept separate from the downloads on purpose. A download is a promise that a
 * song will be there; this is a good chance, and the difference matters on the
 * day it matters. Anything here can be turned into a promise without fetching
 * it again — the audio is already on the disk.
 */
@Composable
fun CachedScreen(onPlay: (List<Track>, Int) -> Unit, onShuffle: (List<Track>) -> Unit) {
    val kept = Cache.items
    val tracks = kept.map { it.track }

    if (kept.isEmpty()) {
        EmptyState(
            Icons.Rounded.Bolt,
            if (Cache.on) "Nothing kept yet" else "Not keeping anything",
            if (Cache.on) {
                "Songs are kept here as you listen to them, with their covers and words, " +
                    "so they still play when the connection doesn't. Nothing to do — play " +
                    "something."
            } else {
                "Turn this back on in Settings › Storage and songs will be kept as you " +
                    "play them."
            },
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Collage(tracks)
                Column(
                    Modifier.weight(1f).padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "KEPT WITHOUT ASKING", color = Blz.muted, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                    )
                    Text(
                        "Kept as you listen", color = Blz.ink, fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${kept.size} songs  ·  ${size(Cache.bytes)} of ${Cache.limitMegabytes} MB  ·  " +
                            "oldest go first",
                        color = Blz.muted, fontSize = 13.sp,
                    )
                    Text(
                        "These play without the network. They are not downloads — the oldest " +
                            "are thrown away to make room — so keep anything you are counting on.",
                        color = Blz.dim, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(Icons.Rounded.PlayArrow, "Play", filled = true) { onPlay(tracks, 0) }
                        Pill(Icons.Rounded.Shuffle, "Shuffle", filled = false) { onShuffle(tracks) }
                        TextButton("Clear all") { Cache.forgetAll() }
                    }
                }
            }
        }

        itemsIndexed(tracks, key = { at, track -> "$at-${track.id}" }) { at, track ->
            SongMenu(track) { Row(track, at + 1) { onPlay(tracks, at) } }
        }
    }
}

@Composable
private fun Row(track: Track, number: Int, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()

    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (hovered.value) {
                Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(19.dp), tint = Blz.ink)
            } else {
                Text("$number", color = Blz.dim, fontSize = 13.sp)
            }
        }
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

        // Turning a good chance into a promise, without fetching a byte: the
        // audio is on the disk already and only its status is changing.
        if (hovered.value) {
            val (pinSource, pinHovered) = rememberHovered()
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .hoverBackground(Blz.hover, pinHovered, pinSource)
                    .clickable { Cache.pin(track.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Download, "Keep this one for good",
                    Modifier.size(17.dp), tint = if (pinHovered.value) Blaze.Amber else Blz.muted,
                )
            }
        }

        if (track.duration.isNotEmpty()) {
            Text(track.duration, color = Blz.dim, fontSize = 12.sp)
        }
        SongSheetButton(track, hovered.value)
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    androidx.compose.foundation.layout.Row(
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
private fun TextButton(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = Blz.muted, fontSize = 12.5.sp)
    }
}

/** Bytes, as somebody would say them out loud. */
private fun size(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "${bytes / (1024 * 1024)} MB"
}
