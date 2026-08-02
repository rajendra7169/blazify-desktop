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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.PlayerState

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The song, given the whole window.
 *
 * Everything that was scattered along the bottom strip is gathered here in one
 * column: what's playing at the top, the cover, the bar you drag, the
 * transport, and everything else in a row beneath it. The strip itself gets out
 * of the way — two sets of the same controls, one of them half the size, is a
 * question about which one to press that shouldn't have to be asked.
 *
 * The words sit beside it when they're open, which is the one pairing worth
 * having on screen at once.
 */
@Composable
fun NowPlayingScreen(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    timerOn: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onOpenTimer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onClose: () -> Unit,
) {
    val track = PlayerState.current
    var themeOpen by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize().then(background)) {
        // ── what this screen is, and the two ways out of its look ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Round(Icons.Rounded.KeyboardArrowDown, "Back to the strip", onClose, 24.dp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "NOW PLAYING", color = Blz.dim, fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
                )
                // Where in the queue this is, since with the browser hidden
                // there is otherwise nothing saying how much is left.
                if (PlayerState.queue.isNotEmpty()) {
                    Text(
                        "${PlayerState.index + 1} of ${PlayerState.queue.size}",
                        color = Blz.muted, fontSize = 12.sp,
                    )
                }
            }
            Box {
                Round(Icons.Rounded.Palette, "Player look", { themeOpen = true }, 20.dp)
                PlayerLookMenu(themeOpen) { themeOpen = false }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Artwork(track?.thumbnail, size = 340.dp, corner = 20.dp)

            Row(
                Modifier.padding(top = 26.dp).widthIn(max = 560.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        track?.title ?: "Nothing playing",
                        color = Blz.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.artist.orEmpty(), color = Blz.muted, fontSize = 14.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // Beside the name rather than down with the rest: liking is
                // about this song, and the row below is about the player.
                if (track != null) {
                    Round(
                        if (PlayerState.currentLiked) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        "Like", PlayerState::toggleLike, 22.dp,
                        tint = if (PlayerState.currentLiked) Blaze.Amber else null,
                    )
                }
            }

            Column(
                Modifier.padding(top = 20.dp).widthIn(max = 560.dp).fillMaxWidth(),
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
                Modifier.padding(top = 18.dp),
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

            // Everything that isn't transport, under the transport. Same order
            // as the strip they replace, so the hand goes to the same place.
            Row(
                Modifier.padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Round(
                    Icons.Rounded.Lyrics, "Lyrics", onToggleLyrics, 20.dp,
                    tint = if (lyricsOpen) Blaze.Amber else null,
                )
                Round(
                    Icons.Rounded.QueueMusic, "Queue", onToggleQueue, 20.dp,
                    tint = if (queueOpen) Blaze.Amber else null,
                )
                Round(
                    Icons.Rounded.Bedtime, "Sleep timer", onOpenTimer, 20.dp,
                    tint = if (timerOn) Blaze.Amber else null,
                )
                if (track != null) {
                    Round(Icons.Rounded.PlaylistAdd, "Add to playlist", onAddToPlaylist, 20.dp)
                    val kept = Downloads.has(track.id)
                    Round(
                        if (kept) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        if (kept) "Kept for offline" else "Keep for offline",
                        PlayerState::downloadCurrent, 20.dp,
                        tint = if (kept) Blaze.Amber else null,
                    )
                }
            }

            // The volume, because with the strip hidden there is nowhere else
            // to reach it without leaving the screen.
            Row(
                Modifier.padding(top = 16.dp).width(220.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Round(
                    when {
                        PlayerState.volume <= 0f -> Icons.Rounded.VolumeOff
                        PlayerState.volume < 0.5f -> Icons.Rounded.VolumeDown
                        else -> Icons.Rounded.VolumeUp
                    },
                    if (PlayerState.volume <= 0f) "Unmute" else "Mute",
                    PlayerState::toggleMute, 18.dp,
                    tint = if (PlayerState.volume <= 0f) Blaze.Amber else null,
                )
                ScrubBar(
                    PlayerState.volume, PlayerState::changeVolume,
                    Modifier.weight(1f), fill = Blz.muted, thickness = 3.dp,
                )
            }

            Box(Modifier.size(20.dp))
        }
    }
}

/**
 * How the player is dressed.
 *
 * The three grounds it can sit on, chosen here rather than only in the settings
 * — this is the screen they change, and a look you have to leave the screen to
 * try is a look nobody tries. More of these are coming; the menu is where they
 * will go.
 */
@Composable
private fun PlayerLookMenu(open: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(220.dp).background(Blz.bar),
    ) {
        Text(
            "PLAYER LOOK", color = Blz.dim, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
        PlayerBackground.entries.forEach { option ->
            val on = option == Look.playerBackground
            val (source, hovered) = rememberHovered()
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable { Look.choosePlayerBackground(option); onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    option.label, color = if (on) Blaze.Amber else Blz.ink,
                    fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                if (on) Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = Blaze.Amber)
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
