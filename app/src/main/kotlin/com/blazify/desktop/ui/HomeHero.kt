package com.blazify.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Account
import java.time.LocalTime

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The top of the home screen.
 *
 * The figure isn't in a box — she's part of the page, bled to the edges with
 * the background fading across her so there's no seam to find. That is the
 * difference between a banner sitting on a screen and a screen that opens with
 * a picture: a card announces itself as a component, and this doesn't.
 *
 * Everything readable stays on the left where the fade is thickest, so the text
 * never has to compete with whatever the picture is doing behind it.
 */
@Composable
fun HomeHero(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = Blz.dark
    val hero = remember(dark) {
        val name = if (dark) "blaze_home_dark.png" else "blaze_home_light.png"
        runCatching { useResource(name) { loadImageBitmap(it) } }.getOrNull()
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(330.dp)) {
        val paneWidth = maxWidth
        // The list this sits in keeps a margin down both sides; the hero
        // reaches past it so the picture meets the window rather than stopping
        // short of it with a strip of background showing.
        val bleed = 26.dp

        Box(
            Modifier
                .offset(x = -bleed)
                .width(paneWidth + bleed * 2)
                .fillMaxHeight(),
        ) {
            // A wash of the accent behind everything, heaviest at the top left
            // where the words are and gone by the bottom where the shelves
            // start, so the two don't meet at a line.
            Box(
                Modifier.fillMaxWidth().fillMaxHeight().background(
                    Brush.verticalGradient(
                        listOf(
                            Blaze.Amber.copy(alpha = if (dark) 0.30f else 0.20f),
                            Blaze.Ember.copy(alpha = if (dark) 0.16f else 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
            )

            hero?.let {
                Box(Modifier.align(Alignment.BottomEnd).fillMaxHeight()) {
                    Image(
                        it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.width(paneWidth * 0.42f).fillMaxHeight(),
                    )
                    // Faded back into the page on every side that meets it, so
                    // there is no edge — the picture ends because the colour
                    // takes over, not because the image stops.
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.horizontalGradient(listOf(Blz.page, Color.Transparent)),
                        ),
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1f to Blz.page,
                            ),
                        ),
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = bleed + 8.dp, end = 24.dp)
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    greeting().uppercase(),
                    color = Blz.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    Account.name ?: "Welcome back",
                    color = Blz.ink,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 48.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Pick up where you left off, or let it choose for you.",
                    color = Blz.muted,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )

                Row(
                    Modifier.padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HeroButton(Icons.Rounded.PlayArrow, "Play", filled = true, onClick = onPlay)
                    HeroButton(Icons.Rounded.Shuffle, "Shuffle", filled = false, onClick = onShuffle)
                }
            }
        }
    }
}

@Composable
private fun HeroButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) {
                    Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                } else {
                    // Outlined rather than filled, so the two buttons read as a
                    // first choice and a second one.
                    Modifier
                        .background(Blz.surface.copy(alpha = 0.55f))
                        .androidxBorder()
                },
            )
            .then(
                if (filled) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 28.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val ink = if (filled) Blaze.OnAmber else Blz.ink
        Icon(icon, label, Modifier.size(20.dp), tint = ink)
        Text(label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A hairline in the accent, which is what makes the second button read as one. */
@Composable
private fun Modifier.androidxBorder(): Modifier =
    border(1.dp, Blaze.Amber.copy(alpha = 0.55f), RoundedCornerShape(999.dp))

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning 🌅"
    in 12..16 -> "Good afternoon ☀️"
    in 17..20 -> "Good evening 🌆"
    else -> "Good night 🌙"
}
