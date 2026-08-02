package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The player, small enough to leave in a corner.
 *
 * Everything here is something you'd reach for without switching windows: what
 * is playing, the three transport keys, the heart, and where you are in the
 * track. Anything you'd have to *browse* for is deliberately absent — that's
 * what the full window is, and there's a button to go back to it.
 */
@Composable
fun MiniPlayer(onExpand: () -> Unit) {
    val track = PlayerState.current

    Column(Modifier.fillMaxSize().background(Blz.page)) {
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Artwork(track?.thumbnail, size = 62.dp, corner = 9.dp)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    track?.title ?: "Nothing playing",
                    color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist.orEmpty(), color = Blz.muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(PlayerState.elapsed, color = Blz.dim, fontSize = 10.5.sp)
                    Text(PlayerState.total, color = Blz.dim, fontSize = 10.5.sp)
                }
            }

            if (track != null) {
                MiniButton(
                    if (PlayerState.currentLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Like", 16.dp,
                    tint = if (PlayerState.currentLiked) Blaze.Amber else null,
                    onClick = PlayerState::toggleLike,
                )
            }
            MiniButton(Icons.Rounded.SkipPrevious, "Previous", 20.dp, onClick = PlayerState::previous)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Blz.ink)
                    .clickable(onClick = PlayerState::toggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (PlayerState.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (PlayerState.playing) "Pause" else "Play",
                    Modifier.size(21.dp),
                    tint = Blz.page,
                )
            }
            MiniButton(Icons.Rounded.SkipNext, "Next", 20.dp, onClick = PlayerState::next)
            MiniButton(Icons.Rounded.OpenInFull, "Full window", 16.dp, onClick = onExpand)
        }

        // Flush against the bottom edge, the full width of the window: at this
        // size a bar with margins around it reads as a stripe of decoration
        // rather than as the position in the track.
        ScrubBar(
            PlayerState.progress,
            PlayerState::seek,
            Modifier.fillMaxWidth(),
            thickness = 4.dp,
        )
    }
}

@Composable
private fun MiniButton(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    val shade by animateColorAsState(
        tint ?: if (hovered.value) Blz.ink else Blz.muted, tween(120), label = "miniTint",
    )
    Box(
        Modifier
            .size(size + 14.dp)
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size), tint = shade)
    }
}
