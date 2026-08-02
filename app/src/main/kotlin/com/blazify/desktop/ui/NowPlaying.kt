package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.launch
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

    // How far down the screen has been pulled, in pixels.
    //
    // The screen follows the pointer rather than waiting for it to pass a
    // threshold and then blinking away. That is the whole difference between a
    // gesture and a shortcut: you can see how far you have to go, you can see
    // it give when you get there, and letting go early puts it back instead of
    // doing nothing at all.
    val slide = remember { Animatable(0f) }
    var tall by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()

    // In from the bottom, the way it will leave.
    LaunchedEffect(Unit) {
        slide.snapTo(tall.coerceAtLeast(600f))
        slide.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
    }

    // Past a third of the way down it is going, and it is easier to say that
    // in distance than in speed — a slow deliberate drag should count as much
    // as a flick.
    val enough = tall * 0.28f

    // The button leaves the same way the gesture does, so the screen has one
    // way of going rather than two that look different.
    fun dismiss() {
        scope.launch {
            slide.animateTo(tall, tween(220, easing = FastOutLinearInEasing))
            onClose()
        }
    }

    fun settle() {
        scope.launch {
            if (slide.value > enough) {
                slide.animateTo(tall, tween(190, easing = FastOutLinearInEasing))
                onClose()
            } else {
                slide.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium))
            }
        }
    }

    // A wash of the accent behind the artwork, or the plain page, or true
    // black. The gradient is bottom-heavy so the controls sit on colour and
    // the cover sits on something closer to the page it came from.
    val background: Modifier = when (if (Look.playerTheme == PlayerTheme.FullArt) PlayerBackground.FollowTheme else Look.playerBackground) {
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

    Box(Modifier.fillMaxSize()) {
    if (Look.playerTheme == PlayerTheme.FullArt) {
        // The cover behind the lot. Scrimmed rather than blurred — a blur is a
        // pass over a large bitmap every frame, and what's wanted here is the
        // colour, not softness.
        //
        // The scrim is light at the top where the picture is and heavier at the
        // bottom where the words and controls are. Even coverage was the first
        // attempt and it hid the artwork completely, which rather defeats a
        // look called Full art.
        Backdrop(track?.thumbnail, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Blz.page.copy(alpha = 0.30f),
                    0.42f to Blz.page.copy(alpha = 0.42f),
                    0.72f to Blz.page.copy(alpha = 0.80f),
                    1f to Blz.page.copy(alpha = 0.94f),
                ),
            ),
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { tall = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                val gone = (slide.value / tall).coerceIn(0f, 1f)
                translationY = slide.value
                // It shrinks and dims a little as it goes, so it reads as
                // receding rather than as a panel being pushed off a shelf.
                val shrink = 1f - gone * 0.06f
                scaleX = shrink
                scaleY = shrink
                alpha = 1f - gone * 0.35f
            }
            .then(background)
            // Pull it down to put it away. The gesture
            // only reaches here when the page underneath has no scrolling left
            // to do, so on a short window dragging still reads the screen and
            // only sends it away once you are at the top of it.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                ) { change, delta ->
                    change.consume()
                    scope.launch {
                        // Downward only, and the further it has come the less
                        // each pixel moves it — the drag gets heavier towards
                        // the end, which is what stops an over-enthusiastic
                        // flick from throwing it off the screen.
                        val eased = if (delta > 0) delta * (1f - (slide.value / tall) * 0.45f) else delta
                        slide.snapTo((slide.value + eased).coerceAtLeast(0f))
                    }
                }
            },
    ) {
        // ── what this screen is, and the two ways out of its look ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Round(Icons.Rounded.KeyboardArrowDown, "Back to the strip", ::dismiss, 24.dp)
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
            Round(Icons.Rounded.Palette, "Player look", { themeOpen = true }, 20.dp)
        }

        // Scrollable, so a short window loses nothing. A centred column that
        // doesn't fit doesn't shrink — it puts its lower half past the bottom
        // edge, which looks exactly like the controls having vanished.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Full art is the cover already, filling the window behind this —
            // a second copy of it in the middle would be the same picture
            // twice. Everything else gets its stage.
            if (Look.playerTheme != PlayerTheme.FullArt) {
                PlayerStage(
                    theme = Look.playerTheme,
                    artwork = track?.thumbnail,
                    side = 340.dp,
                    playing = PlayerState.playing,
                    progress = PlayerState.progress,
                )
            } else {
                Box(Modifier.size(1.dp, 200.dp))
            }

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

            // Sized for a screen you look at from across a room rather than
            // a strip you glance down at. Skip is larger than shuffle and
            // repeat, and play larger again: how often a control is pressed is
            // what should decide how big it is.
            Row(
                Modifier.padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Round(
                    Icons.Rounded.Shuffle, "Shuffle", PlayerState::toggleShuffle, 26.dp,
                    tint = if (PlayerState.shuffling) Blaze.Amber else null,
                )
                Round(Icons.Rounded.SkipPrevious, "Previous", PlayerState::previous, 40.dp)
                Box(
                    Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                        .clickable(onClick = PlayerState::toggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (PlayerState.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (PlayerState.playing) "Pause" else "Play",
                        Modifier.size(44.dp),
                        tint = Blaze.OnAmber,
                    )
                }
                Round(Icons.Rounded.SkipNext, "Next", PlayerState::next, 40.dp)
                Round(
                    if (PlayerState.repeat == PlayerState.Repeat.One) Icons.Rounded.RepeatOne
                    else Icons.Rounded.Repeat,
                    "Repeat", PlayerState::cycleRepeat, 26.dp,
                    tint = if (PlayerState.repeat != PlayerState.Repeat.Off) Blaze.Amber else null,
                )
            }

            // Everything that isn't transport, under the transport, and
            // named. Five unlabelled glyphs is a memory test — and on a screen
            // with this much room, refusing to say what they do is a choice
            // rather than a constraint.
            //
            // Lyrics at the right end and the queue beside it, since both open
            // the panel that slides in from that side — the button and the
            // thing it summons on the same edge. What belongs to the song
            // rather than to the screen goes left.
            Row(
                Modifier.padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (track != null) {
                    Labelled(Icons.Rounded.PlaylistAdd, "Playlist", onClick = onAddToPlaylist)
                    val kept = Downloads.has(track.id)
                    Labelled(
                        if (kept) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        if (kept) "Kept" else "Download",
                        on = kept,
                        onClick = PlayerState::downloadCurrent,
                    )
                }
                Labelled(
                    Icons.Rounded.Bedtime, "Timer", on = timerOn, onClick = onOpenTimer,
                )
                Labelled(
                    Icons.Rounded.QueueMusic, "Queue", on = queueOpen, onClick = onToggleQueue,
                )
                Labelled(
                    Icons.Rounded.Lyrics, "Lyrics", on = lyricsOpen, onClick = onToggleLyrics,
                )
            }

            // The volume, because with the strip hidden there is nowhere else
            // to reach it without leaving the screen.
            Row(
                Modifier.padding(top = 22.dp).width(340.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Round(
                    when {
                        PlayerState.volume <= 0f -> Icons.Rounded.VolumeOff
                        PlayerState.volume < 0.5f -> Icons.Rounded.VolumeDown
                        else -> Icons.Rounded.VolumeUp
                    },
                    if (PlayerState.volume <= 0f) "Unmute" else "Mute",
                    PlayerState::toggleMute, 24.dp,
                    tint = if (PlayerState.volume <= 0f) Blaze.Amber else null,
                )
                // Thicker than the strip's, and wider: a bar you set by
                // dragging wants to be hittable without aiming.
                ScrubBar(
                    PlayerState.volume, PlayerState::changeVolume,
                    Modifier.weight(1f), fill = Blz.muted, thickness = 6.dp,
                )
            }

            Box(Modifier.size(20.dp))
        }
    }

    if (themeOpen) PlayerThemeSheet { themeOpen = false }
    }
}

@Composable
private fun Labelled(
    icon: ImageVector,
    label: String,
    on: Boolean = false,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    val shade by animateColorAsState(
        if (on) Blaze.Amber else if (hovered.value) Blz.ink else Blz.muted,
        tween(120), label = "labelledTint",
    )
    Column(
        Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = shade)
        Text(
            label, color = shade, fontSize = 11.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
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
