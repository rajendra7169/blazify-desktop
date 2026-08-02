package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
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
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
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
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
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

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** What the bar needs to draw itself. Nothing here knows how audio works. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val position: Float = 0f,      // 0..1
    val elapsed: String = "0:00",
    val duration: String = "0:00",
    val playing: Boolean = false,
    val liked: Boolean = false,
    val kept: Boolean = false,
    /** Null unless a download is under way, 0..1 while it is. */
    val keeping: Float? = null,
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
    volume: Float,
    onPlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onKeep: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolume: (Float) -> Unit,
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
            // Tall enough that a hovered control's circle has room to sit in.
            // At the old height they landed against the top edge, which read as
            // the bar being too small for its own contents.
            .height(88.dp)
            .background(Blz.bar)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { NowPlayingCell(now, onToggleLike, onKeep) }

        Transport(now, onPlayPause, onSeek, onNext, onPrevious)

        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarToggle(Icons.Rounded.CloseFullscreen, "Mini", false, WindowMode::toggleMini)
            BarToggle(Icons.Rounded.Lyrics, "Lyrics", lyricsOpen, onToggleLyrics)
            BarToggle(Icons.Rounded.QueueMusic, "Queue", queueOpen, onToggleQueue)
            // The icon is the mute button, and it says which state you're in —
            // a speaker that never changes is decoration, not a control.
            TransportButton(
                when {
                    volume <= 0f -> Icons.Rounded.VolumeOff
                    volume < 0.5f -> Icons.Rounded.VolumeDown
                    else -> Icons.Rounded.VolumeUp
                },
                if (volume <= 0f) "Unmute" else "Mute",
                17.dp,
                onClick = onToggleMute,
                tint = if (volume <= 0f) Blaze.Amber else null,
            )
            ScrubBar(volume, onVolume, Modifier.width(76.dp), fill = Blz.muted, thickness = 3.dp)
        }
    }
}

@Composable
private fun NowPlayingCell(now: NowPlaying?, onToggleLike: () -> Unit, onKeep: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Artwork(now?.artwork, size = 44.dp, corner = 7.dp)
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(
                now?.title ?: "Nothing playing",
                color = Blz.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                now?.artist.orEmpty(),
                color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (now != null) {
            TransportButton(
                if (now.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (now.liked) "Unlike" else "Like",
                17.dp,
                tint = if (now.liked) Blaze.Amber else Blz.muted,
                onClick = onToggleLike,
            )
            KeepButton(now, onKeep)
        }
    }
}

@Composable
private fun Transport(
    now: NowPlaying?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Column(
        // The bar you drag is the control people use most, and a short one is
        // both harder to aim at and coarser to seek with — every pixel is worth
        // more seconds. It gets the width.
        Modifier.width(620.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransportButton(Icons.Rounded.Shuffle, "Shuffle", 17.dp)
            TransportButton(Icons.Rounded.SkipPrevious, "Previous", 22.dp, onClick = onPrevious)
            PlayButton(now?.playing == true, onPlayPause)
            TransportButton(Icons.Rounded.SkipNext, "Next", 22.dp, onClick = onNext)
            TransportButton(Icons.Rounded.Repeat, "Repeat", 17.dp)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(now?.elapsed ?: "0:00", color = Blz.dim, fontSize = 11.sp)
            ScrubBar(now?.position ?: 0f, onSeek, Modifier.weight(1f), thickness = 5.dp)
            Text(now?.duration ?: "0:00", color = Blz.dim, fontSize = 11.sp)
        }
    }
}

/**
 * Keep for offline, and how far along that is.
 *
 * The ring around the icon is the progress — a bar somewhere else on screen
 * would leave you hunting for which song it belonged to.
 */
@Composable
private fun KeepButton(now: NowPlaying, onKeep: () -> Unit) {
    val fraction = now.keeping
    Box(contentAlignment = Alignment.Center) {
        TransportButton(
            when {
                now.kept -> Icons.Rounded.DownloadDone
                fraction != null -> Icons.Rounded.Downloading
                else -> Icons.Rounded.Download
            },
            when {
                now.kept -> "Kept for offline"
                fraction != null -> "Keeping"
                else -> "Keep for offline"
            },
            17.dp,
            onClick = if (now.kept || fraction != null) null else onKeep,
            tint = if (now.kept) Blaze.Amber else null,
        )
        if (fraction != null) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.size(28.dp),
                color = Blaze.Amber,
                trackColor = Blz.surfaceHigh,
                strokeWidth = 2.dp,
            )
        }
    }
}

/**
 * A transport control with room around it.
 *
 * The circle is drawn around the icon rather than on it, so hovering lights a
 * shape that sits inside the bar instead of running up against its edge.
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null,
    tint: Color? = null,
) {
    val (source, hovered) = rememberHovered()
    val shade by animateColorAsState(
        tint ?: if (hovered.value) Blz.ink else Blz.muted, tween(120), label = "transportTint",
    )
    Box(
        Modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .hoverBackground(Blz.hover, hovered, source)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size), tint = shade)
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .size(38.dp)
            .hoverLift(hovered, to = 1.06f)
            .clip(CircleShape)
            .background(Blz.ink)
            .hoverable(source)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause" else "Play",
            Modifier.size(22.dp),
            tint = Blz.page,
        )
    }
}

@Composable
private fun BarToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, label, Modifier.size(16.dp), tint = if (on) Blaze.Amber else Blz.muted)
        Text(label, color = if (on) Blaze.Amber else Blz.muted, fontSize = 11.5.sp)
    }
}

/** A flat two-tone bar. Used for both progress and volume. */
@Composable
private fun Meter(fraction: Float, modifier: Modifier = Modifier, fill: Color = Blz.muted) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Blz.surfaceHigh)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(fill),
        )
    }
}
