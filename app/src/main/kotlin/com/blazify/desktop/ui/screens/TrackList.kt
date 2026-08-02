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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
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
import com.blazify.desktop.data.Library
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
 * A screen that is one list of songs, with a title over it.
 *
 * Liked songs and history are the same thing wearing different words, and a
 * page of songs is exactly as useful empty as it is full — so the empty state
 * says what would put something here rather than apologising for the blank.
 */
@Composable
fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    empty: String,
    emptyIcon: ImageVector = Icons.Rounded.Favorite,
    emptyDetail: String = "",
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    action: Pair<String, () -> Unit>? = null,
    onBack: (() -> Unit)? = null,
    kind: String = "Playlist",
) {
    if (tracks.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            onBack?.let { back ->
                val (source, hovered) = rememberHovered()
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .padding(start = 26.dp, top = 22.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .hoverBackground(Blz.hover, hovered, source)
                        .clickable(onClick = back)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(17.dp), tint = Blz.muted)
                    Text("Back", color = Blz.muted, fontSize = 13.sp)
                }
            }
            EmptyState(emptyIcon, title, emptyDetail.ifBlank { empty })
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        onBack?.let { back ->
            item {
                val (source, hovered) = rememberHovered()
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .hoverBackground(Blz.hover, hovered, source)
                        .clickable(onClick = back)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(17.dp), tint = Blz.muted)
                    Text("Back", color = Blz.muted, fontSize = 13.sp)
                }
            }
        }

        item {
            androidx.compose.foundation.layout.Row(
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
                        kind.uppercase(), color = Blz.muted, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                    )
                    Text(
                        title, color = Blz.ink, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            "${tracks.size} songs",
                            length(tracks),
                        ).joinToString("  ·  "),
                        color = Blz.muted, fontSize = 13.sp,
                    )

                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(Icons.Rounded.PlayArrow, "Play", filled = true) { onPlay(tracks, 0) }
                        Pill(Icons.Rounded.Shuffle, "Shuffle", filled = false) { onShuffle(tracks) }
                        action?.let { (label, onClick) -> TextAction(label, onClick) }
                    }
                }
            }
        }

        itemsIndexed(tracks, key = { at, track -> "$at-${track.id}" }) { at, track ->
            SongMenu(track) { Row(track, at + 1) { onPlay(tracks, at) } }
        }
    }
}

/**
 * Cover art for a list that has none of its own.
 *
 * A playlist is only ever the songs in it, so the first four of them stand in.
 * Four rather than one because a single cover claims the list is that record —
 * a grid says at a glance that it's a collection, before a word is read.
 */
@Composable
private fun Collage(tracks: List<Track>) {
    val covers = tracks.mapNotNull { it.thumbnail }.take(4)

    Box(
        Modifier
            .size(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Blz.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            covers.size >= 4 -> Column {
                androidx.compose.foundation.layout.Row {
                    Artwork(covers[0], size = 105.dp, corner = 0.dp)
                    Artwork(covers[1], size = 105.dp, corner = 0.dp)
                }
                androidx.compose.foundation.layout.Row {
                    Artwork(covers[2], size = 105.dp, corner = 0.dp)
                    Artwork(covers[3], size = 105.dp, corner = 0.dp)
                }
            }
            covers.isNotEmpty() -> Artwork(covers.first(), size = 210.dp, corner = 0.dp)
            else -> Icon(Icons.Rounded.QueueMusic, null, Modifier.size(46.dp), tint = Blz.dim)
        }
    }
}

/** How long the whole thing runs, when every song has said. */
private fun length(tracks: List<Track>): String? {
    val seconds = tracks.sumOf { it.durationSeconds ?: 0 }
    if (seconds <= 0) return null
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}

@Composable
private fun Row(track: Track, number: Int, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    val liked = Library.isLiked(track.id)

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
        if (liked) {
            Icon(Icons.Rounded.Favorite, "Liked", Modifier.size(15.dp), tint = Blaze.Amber)
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
