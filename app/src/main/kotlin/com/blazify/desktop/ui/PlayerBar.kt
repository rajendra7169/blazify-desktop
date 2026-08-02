package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
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
import androidx.compose.ui.graphics.Brush
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
    onOpenTimer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onExpand: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    shuffling: Boolean,
    repeat: Int,
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    timerOn: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val narrow = maxWidth < 1000.dp
    Row(
        Modifier
            .fillMaxWidth()
            // Tall enough that a hovered control's circle has room to sit in.
            // At the old height they landed against the top edge, which read as
            // the bar being too small for its own contents.
            .height(88.dp)
            .background(Blz.bar)
            // A wash of whatever is playing, laid over the bar's own colour
            // rather than replacing it — enough that the strip belongs to the
            // song, not so much that the controls have to fight it.
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Blaze.Amber.copy(alpha = if (now == null) 0f else 0.13f),
                        Blaze.Ember.copy(alpha = if (now == null) 0f else 0.05f),
                        Color.Transparent,
                    ),
                ),
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The whole cell, not only the cover and the title: the empty space
        // beside them is still "what's playing", and clicking a thing to see
        // more of it should not depend on hitting the words. The buttons
        // inside take their own clicks first, so nothing is stolen from them.
        val (cellSource, cellHovered) = rememberHovered()
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .hoverBackground(Color.Transparent, cellHovered, cellSource)
                .clickable(
                    enabled = now != null,
                    indication = null,
                    interactionSource = cellSource,
                    onClick = onExpand,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            NowPlayingCell(now, onToggleLike, onKeep, onAddToPlaylist, onExpand)
        }

        // Weighted rather than fixed, so the bar you drag grows with the
        // window instead of stranding it at one width on a wide screen. The
        // side columns match each other, which keeps the play button centred
        // on the window rather than on whatever space a long title left over.
        Transport(
            now, onPlayPause, onSeek, onNext, onPrevious,
            onToggleShuffle, onCycleRepeat, shuffling, repeat,
            Modifier.weight(1.6f),
        )

        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 16.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                Icons.Rounded.Bedtime, "Sleep timer", 17.dp,
                onClick = onOpenTimer,
                tint = if (timerOn) Blaze.Amber else null,
            )
            // Below this the three labels and the volume bar stop fitting
            // beside the transport, and the row starts squeezing the title.
            // The icons carry the same meaning in a third of the width, so the
            // words are what goes.
            val roomy = narrow.not()
            BarToggle(Icons.Rounded.CloseFullscreen, "Mini", false, roomy, WindowMode::toggleMini)
            BarToggle(Icons.Rounded.Lyrics, "Lyrics", lyricsOpen, roomy, onToggleLyrics)
            BarToggle(Icons.Rounded.QueueMusic, "Queue", queueOpen, roomy, onToggleQueue)
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
            ScrubBar(
                volume, onVolume, Modifier.width(if (narrow) 54.dp else 76.dp),
                fill = Blz.muted, thickness = 3.dp,
            )
        }
    }
    }
}

@Composable
private fun NowPlayingCell(
    now: NowPlaying?,
    onToggleLike: () -> Unit,
    onKeep: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // The artwork and the title are the way into the full view — clicking
        // what's playing to see it bigger needs no button to explain it.
        val (source, hovered) = rememberHovered()
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .hoverBackground(Blz.hover, hovered, source)
                .clickable(enabled = now != null, onClick = onExpand)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Artwork(now?.artwork, size = 44.dp, corner = 7.dp)
            Column(Modifier.widthIn(max = 200.dp)) {
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
            TransportButton(
                Icons.Rounded.PlaylistAdd, "Add to playlist", 18.dp,
                onClick = onAddToPlaylist,
            )
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
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    shuffling: Boolean,
    repeat: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        // The bar you drag is the control people use most, and a short one is
        // both harder to aim at and coarser to seek with — every pixel is worth
        // more seconds. It gets whatever the window can spare.
        modifier.widthIn(min = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransportButton(
                Icons.Rounded.Shuffle, if (shuffling) "Stop shuffling" else "Shuffle", 17.dp,
                onClick = onToggleShuffle,
                tint = if (shuffling) Blaze.Amber else null,
            )
            TransportButton(Icons.Rounded.SkipPrevious, "Previous", 22.dp, onClick = onPrevious)
            PlayButton(now?.playing == true, onPlayPause)
            TransportButton(Icons.Rounded.SkipNext, "Next", 22.dp, onClick = onNext)
            // One icon for three states: repeating a single track gets its own
            // drawing, since "on" alone can't say which kind of on it is.
            TransportButton(
                if (repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (repeat) {
                    1 -> "Repeating the queue"
                    2 -> "Repeating this track"
                    else -> "Repeat"
                },
                17.dp,
                onClick = onCycleRepeat,
                tint = if (repeat > 0) Blaze.Amber else null,
            )
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
private fun BarToggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    labelled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The label is still the icon's description when it isn't drawn, so
        // nothing is lost to anyone reading the screen rather than looking at
        // it.
        Icon(icon, label, Modifier.size(16.dp), tint = if (on) Blaze.Amber else Blz.muted)
        if (labelled) {
            Text(label, color = if (on) Blaze.Amber else Blz.muted, fontSize = 11.5.sp)
        }
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
