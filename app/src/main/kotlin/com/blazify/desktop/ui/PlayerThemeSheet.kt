package com.blazify.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Trying on a look before wearing it.
 *
 * The list is on the left and what it does is on the right, drawn with the song
 * that is actually playing rather than a stock square — a preview of somebody
 * else's album tells you nothing about how yours will sit in it.
 *
 * Pointing at a name previews it; clicking applies it. That split is the whole
 * point of the sheet: you can look through all five without committing to any,
 * and the one you end up on is the one you chose rather than the last one you
 * happened to touch.
 */
@Composable
fun PlayerThemeSheet(onDismiss: () -> Unit) {
    val current = Look.playerTheme
    var previewing by remember { mutableStateOf(current) }
    val track = PlayerState.current

    Box(
        Modifier.fillMaxSize().background(Blaze.Scrim).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .width(720.dp)
                .height(470.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {},
        ) {
            Column(
                Modifier.width(276.dp).fillMaxHeight().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "PLAYER LOOK", color = Blz.dim, fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                )
                // Scrolls, so a look added later cannot fall off the bottom the
                // way the last one just did.
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                PlayerTheme.entries.forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        wearing = theme == current,
                        showing = theme == previewing,
                        onHover = { previewing = theme },
                    ) {
                        Look.choosePlayerTheme(theme)
                        onDismiss()
                    }
                }
                }
            }

            Box(Modifier.fillMaxHeight().width(1.dp).background(Blz.line))

            Column(
                Modifier.weight(1f).fillMaxHeight().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // One frame, the same size for every look, filled by whichever
                // is being shown. Letting each preview size itself made the
                // pane grow and shrink as the pointer moved down the list —
                // the whole card jumped, which read as a glitch rather than as
                // a choice. The frame is fixed; only its contents change.
                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Crossfaded rather than swapped, so moving down the list
                    // reads as one thing changing rather than five flickering.
                    Crossfade(
                        previewing,
                        animationSpec = tween(220),
                        label = "themePreview",
                        modifier = Modifier.fillMaxSize(),
                    ) { theme ->
                        if (theme == PlayerTheme.FullArt) {
                            // Full art has no centrepiece — it *is* the page. A
                            // square of the cover here would preview Classic
                            // under a different name, so this fills the frame
                            // with the whole screen in miniature.
                            FullArtPreview(track)
                        } else {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                PlayerStage(
                                    theme = theme,
                                    artwork = track?.thumbnail,
                                    side = 190.dp,
                                    playing = PlayerState.playing,
                                    progress = PlayerState.progress,
                                )
                                // Drawn but not live — the preview is about the
                                // artwork, and a play button that worked here
                                // would move the song while you looked at it.
                                Row(
                                    Modifier.padding(top = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.SkipPrevious, null,
                                        Modifier.size(20.dp), tint = Blz.dim,
                                    )
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (PlayerState.playing) Icons.Rounded.Pause
                                            else Icons.Rounded.PlayArrow,
                                            null, Modifier.size(22.dp), tint = Blaze.OnAmber,
                                        )
                                    }
                                    Icon(
                                        Icons.Rounded.SkipNext, null,
                                        Modifier.size(20.dp), tint = Blz.dim,
                                    )
                                }
                            }
                        }
                    }
                }

                // Below the frame and outside the crossfade, so the words stay
                // put while the picture above them changes.
                Text(
                    // Full art already names the song inside the card, so this
                    // line names the look instead. Same line either way, so
                    // nothing moves as the pointer travels down the list.
                    if (previewing == PlayerTheme.FullArt) previewing.label
                    else track?.title ?: "Nothing playing",
                    color = Blz.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    previewing.blurb, color = Blz.dim, fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * The whole window, shrunk to a card.
 *
 * The real song, the real position, the real accent — placeholder bars told you
 * where things would sit but nothing about whether your covers work under white
 * type, which is the only question this preview exists to answer.
 */
@Composable
private fun FullArtPreview(track: com.blazify.desktop.data.Track?) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Blz.page),
    ) {
        Backdrop(track?.thumbnail, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.52f),
                    0.30f to Color.Black.copy(alpha = 0.12f),
                    0.52f to Color.Black.copy(alpha = 0.34f),
                    0.74f to Color.Black.copy(alpha = 0.70f),
                    1f to Color.Black.copy(alpha = 0.92f),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "NOW PLAYING", color = Color.White.copy(alpha = 0.85f), fontSize = 8.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            )

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    track?.title ?: "Nothing playing",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    track?.artist.orEmpty(),
                    color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )

                // The bar sits where it sits, at the position the song is
                // actually at — drawn rather than the real ScrubBar, since a
                // preview you could scrub would move the music while you were
                // looking at how it looks.
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.26f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(PlayerState.progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                    Text(
                        PlayerState.elapsed,
                        color = Color.White.copy(alpha = 0.55f), fontSize = 8.5.sp,
                    )
                    Box(Modifier.weight(1f))
                    Text(
                        PlayerState.total,
                        color = Color.White.copy(alpha = 0.55f), fontSize = 8.5.sp,
                    )
                }

                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(16.dp), tint = Color.White)
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (PlayerState.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, Modifier.size(17.dp), tint = Blaze.OnAmber,
                        )
                    }
                    Icon(Icons.Rounded.SkipNext, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: PlayerTheme,
    wearing: Boolean,
    showing: Boolean,
    onHover: () -> Unit,
    onPick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    // In an effect, not in the composition. Calling onHover() straight from the
    // body writes state while that state is being read, which Compose answers
    // by recomposing again — the flicker between two rows was this loop, not
    // the crossfade.
    LaunchedEffect(hovered.value) {
        if (hovered.value) onHover()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (showing) Modifier.background(Blz.surfaceHigh) else Modifier,
            )
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                theme.label,
                color = if (wearing) Blaze.Amber else Blz.ink,
                fontSize = 13.5.sp,
                fontWeight = if (wearing) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                theme.blurb, color = Blz.dim, fontSize = 11.sp,
                maxLines = 2, lineHeight = 15.sp,
            )
        }
        if (wearing) Icon(Icons.Rounded.Check, "In use", Modifier.size(16.dp), tint = Blaze.Amber)
    }
}
