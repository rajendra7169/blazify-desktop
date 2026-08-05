package com.blazify.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Feeds
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The parts of an hour.
 *
 * An episode is four or five things — an introduction, the subject, the bit
 * with the guest, the recommendations at the end — and without them on screen
 * finding the part somebody mentioned means dragging a bar and listening for
 * it. Where a show publishes them, they belong beside the words: both answer
 * "where in this am I", one in sentences and one in headings.
 *
 * The one you are inside is marked, so the list also answers it without being
 * read.
 */
@Composable
fun ChapterList(track: Track?, position: Double, modifier: Modifier = Modifier) {
    var parts by remember(track?.id) { mutableStateOf<List<Feeds.Part>>(emptyList()) }

    LaunchedEffect(track?.id) {
        parts = track?.parts?.let { Feeds.parts(it) }.orEmpty()
    }

    if (parts.isEmpty()) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "In this episode",
            color = Blz.muted, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp,
            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
        )

        parts.forEachIndexed { at, part ->
            val until = parts.getOrNull(at + 1)?.at ?: Double.MAX_VALUE
            Chapter(part, inside = position >= part.at && position < until)
        }
    }
}

@Composable
private fun Chapter(part: Feeds.Part, inside: Boolean) {
    val (source, hovered) = rememberHovered()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { PlayerState.seekTo(part.at) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(52.dp)) {
            // The moment, until the pointer arrives — then what pressing it
            // does. One column, so nothing moves sideways on hover.
            if (hovered.value) {
                Icon(
                    Icons.Rounded.PlayArrow, "Jump here",
                    Modifier.size(18.dp), tint = Blz.ink,
                )
            } else {
                Text(
                    clock(part.at),
                    color = if (inside) Blaze.Amber else Blz.dim,
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            part.title,
            color = if (inside) Blaze.Amber else Blz.ink,
            fontSize = 13.sp,
            fontWeight = if (inside) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A moment, as somebody would read it aloud. */
private fun clock(seconds: Double): String {
    val whole = seconds.toInt()
    val hours = whole / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, (whole % 3600) / 60, whole % 60)
    else "%d:%02d".format(whole / 60, whole % 60)
}
