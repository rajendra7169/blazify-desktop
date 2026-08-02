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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Plays
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.EmptyState
import com.blazify.desktop.ui.SongSheetButton
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What you actually played, counted.
 *
 * Not what you liked — liking is a decision, and this is a record. The two
 * disagree more than anyone expects, which is the whole reason it's worth
 * looking at: the song you had on forty times this month is rarely the one you
 * would have named.
 */
@Composable
fun TopSongsScreen(
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
) {
    var span by remember { mutableStateOf(Plays.Span.Month) }
    val known = remember(Library.history, Library.liked) { Library.known() }
    val top = remember(span, Plays.all, known) { Plays.top(span, known) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Top songs", color = Blz.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (val plays = Plays.countIn(span)) {
                            0 -> "Nothing counted yet"
                            1 -> "1 play ${span.label.lowercase()}"
                            else -> "$plays plays ${span.label.lowercase()}"
                        },
                        color = Blz.muted, fontSize = 13.sp,
                    )
                }
                if (top.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Action(Icons.Rounded.PlayArrow, "Play", filled = true) {
                            onPlay(top.map { it.first }, 0)
                        }
                        Action(Icons.Rounded.Shuffle, "Shuffle", filled = false) {
                            onShuffle(top.map { it.first })
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Plays.Span.entries.forEach { option ->
                    Chip(option.label, option == span) { span = option }
                }
            }
        }

        if (top.isEmpty()) {
            EmptyState(
                Icons.Rounded.TrendingUp,
                "Nothing counted yet",
                "Play something and it starts adding up here. This counts every play, so " +
                    "the song you have on repeat is the song that wins — which is rarely " +
                    "the one you would have guessed.",
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 26.dp, end = 26.dp, bottom = 24.dp,
            ),
        ) {
            items(top, key = { (track, _) -> track.id }) { (track, count) ->
                Line(track, count, top.first().second) {
                    onPlay(top.map { it.first }, top.indexOfFirst { it.first.id == track.id })
                }
            }
        }
    }
}

/**
 * One song, with how often behind it.
 *
 * The count is drawn as a bar relative to the most-played, because "37" means
 * nothing on its own and "twice as long as the one below it" means everything.
 */
@Composable
private fun Line(track: Track, count: Int, most: Int, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(track.thumbnail, size = 42.dp, corner = 6.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title, color = Blz.ink, fontSize = 13.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .width(90.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Blz.surfaceHigh)
                .padding(0.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((count.toFloat() / most).coerceIn(0.06f, 1f))
                    .size(width = 90.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
            )
        }
        Text(
            "$count", color = Blz.muted, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(30.dp),
        )
        SongSheetButton(track, hovered.value)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (on) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surfaceHigh),
            )
            .then(
                if (on) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (on) Blaze.OnAmber else Blz.muted,
            fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surfaceHigh),
            )
            .then(
                if (filled) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val ink = if (filled) Blaze.OnAmber else Blz.ink
        Icon(icon, label, Modifier.size(18.dp), tint = ink)
        Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
