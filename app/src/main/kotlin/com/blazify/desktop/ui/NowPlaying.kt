package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import com.blazify.desktop.PlayerState

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The song, given the whole window.
 *
 * Nothing here is new — every control exists in the strip along the bottom
 * already. What's different is the artwork, and that is the entire point: this
 * is the view you leave open while listening rather than while looking for
 * something, so the cover gets the room and the controls get out of the way.
 *
 * The words sit beside it when they're open, which is the one pairing worth
 * having on screen at once.
 */
@Composable
fun NowPlayingScreen(lyricsOpen: Boolean, onToggleLyrics: () -> Unit, onClose: () -> Unit) {
    val track = PlayerState.current

    // A wash of the accent behind the artwork, or the plain page, or true
    // black. The gradient is bottom-heavy so the controls sit on colour and
    // the cover sits on something closer to the page it came from.
    val background: Modifier = when (Look.playerBackground) {
        PlayerBackground.FollowTheme -> Modifier.background(Blz.page)
        PlayerBackground.PureBlack -> Modifier.background(Color.Black)
        PlayerBackground.Gradient -> Modifier.background(
            Brush.verticalGradient(
                listOf(
                    Blz.page,
                    Blaze.Amber.copy(alpha = 0.10f),
                    Blaze.Ember.copy(alpha = 0.22f),
                ),
            ),
        )
    }

    Row(Modifier.fillMaxSize().then(background)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Round(Icons.Rounded.CloseFullscreen, "Back", onClose)
            }

            Artwork(track?.thumbnail, size = 380.dp, corner = 20.dp)

            Column(
                Modifier.padding(top = 30.dp).widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    track?.title ?: "Nothing playing",
                    color = Blz.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist.orEmpty(), color = Blz.muted, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                Modifier.padding(top = 26.dp).widthIn(max = 560.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ScrubBar(PlayerState.progress, PlayerState::seek, Modifier.fillMaxWidth(), thickness = 6.dp)
                Row(Modifier.fillMaxWidth()) {
                    Text(PlayerState.elapsed, color = Blz.dim, fontSize = 12.sp)
                    Box(Modifier.weight(1f))
                    Text(PlayerState.total, color = Blz.dim, fontSize = 12.sp)
                }
            }

            Row(
                Modifier.padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Round(
                    Icons.Rounded.Shuffle, "Shuffle", PlayerState::toggleShuffle, 20.dp,
                    tint = if (PlayerState.shuffling) Blaze.Amber else null,
                )
                Round(Icons.Rounded.SkipPrevious, "Previous", PlayerState::previous, 30.dp)
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                        .clickable(onClick = PlayerState::toggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (PlayerState.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (PlayerState.playing) "Pause" else "Play",
                        Modifier.size(34.dp),
                        tint = Blaze.OnAmber,
                    )
                }
                Round(Icons.Rounded.SkipNext, "Next", PlayerState::next, 30.dp)
                Round(
                    if (PlayerState.repeat == PlayerState.Repeat.One) Icons.Rounded.RepeatOne
                    else Icons.Rounded.Repeat,
                    "Repeat", PlayerState::cycleRepeat, 20.dp,
                    tint = if (PlayerState.repeat != PlayerState.Repeat.Off) Blaze.Amber else null,
                )
            }

            if (track != null) {
                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Round(
                        if (PlayerState.currentLiked) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        "Like", PlayerState::toggleLike, 20.dp,
                        tint = if (PlayerState.currentLiked) Blaze.Amber else null,
                    )
                    Round(
                        Icons.Rounded.Lyrics, "Lyrics", onToggleLyrics, 20.dp,
                        tint = if (lyricsOpen) Blaze.Amber else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun Round(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 20.dp,
    tint: Color? = null,
) {
    val (source, hovered) = rememberHovered()
    val shade by animateColorAsState(
        tint ?: if (hovered.value) Blz.ink else Blz.muted, tween(120), label = "nowTint",
    )
    Box(
        Modifier
            .size(size + 18.dp)
            .clip(CircleShape)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size), tint = shade)
    }
}
