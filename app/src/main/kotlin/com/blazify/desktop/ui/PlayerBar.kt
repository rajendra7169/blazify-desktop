package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** What the bar needs to draw itself. Nothing here knows how audio works. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val position: Float = 0f,      // 0..1
    val elapsed: String = "0:00",
    val duration: String = "0:00",
    val playing: Boolean = false,
    val liked: Boolean = false,
)

/**
 * The transport strip pinned across the bottom of every screen.
 *
 * Three columns: what's playing, the controls, and the toggles. The middle one
 * is centred on the window rather than on the space left over, so the play
 * button doesn't drift as a long title pushes it around.
 */
@Composable
fun PlayerBar(
    now: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(Color(0xFF111114))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { NowPlayingCell(now) }

        Transport(now, onPlayPause, onNext, onPrevious)

        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarToggle(Icons.Rounded.Lyrics, "Lyrics", lyricsOpen, onToggleLyrics)
            BarToggle(Icons.Rounded.QueueMusic, "Queue", queueOpen, onToggleQueue)
            Icon(Icons.Rounded.VolumeUp, "Volume", Modifier.size(17.dp), tint = Blaze.Muted)
            Meter(0.7f, Modifier.width(76.dp))
        }
    }
}

@Composable
private fun NowPlayingCell(now: NowPlaying?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Blaze.SurfaceHigh),
        )
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(
                now?.title ?: "Nothing playing",
                color = Blaze.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                now?.artist.orEmpty(),
                color = Blaze.Muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (now != null) {
            Icon(
                if (now.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Like",
                Modifier.size(16.dp),
                tint = if (now.liked) Blaze.Amber else Blaze.Muted,
            )
        }
    }
}

@Composable
private fun Transport(
    now: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Column(
        Modifier.width(430.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", Modifier.size(16.dp), tint = Blaze.Muted)
            Icon(
                Icons.Rounded.SkipPrevious, "Previous",
                Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onPrevious), tint = Blaze.Muted,
            )
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Blaze.Ink)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (now?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (now?.playing == true) "Pause" else "Play",
                    Modifier.size(18.dp),
                    tint = Blaze.Night,
                )
            }
            Icon(
                Icons.Rounded.SkipNext, "Next",
                Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onNext), tint = Blaze.Muted,
            )
            Icon(Icons.Rounded.Repeat, "Repeat", Modifier.size(16.dp), tint = Blaze.Muted)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(now?.elapsed ?: "0:00", color = Blaze.Dim, fontSize = 10.5.sp)
            Meter(now?.position ?: 0f, Modifier.weight(1f), Blaze.Amber)
            Text(now?.duration ?: "0:00", color = Blaze.Dim, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun BarToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, label, Modifier.size(16.dp), tint = if (on) Blaze.Amber else Blaze.Muted)
        Text(label, color = if (on) Blaze.Amber else Blaze.Muted, fontSize = 11.5.sp)
    }
}

/** A flat two-tone bar. Used for both progress and volume. */
@Composable
private fun Meter(fraction: Float, modifier: Modifier = Modifier, fill: Color = Blaze.Muted) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A2A30))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(fill),
        )
    }
}
