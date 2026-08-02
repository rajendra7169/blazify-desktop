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

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pane = maxWidth
        // The list this sits in keeps a margin down both sides; the hero
        // reaches past it so the picture meets the window rather than stopping
        // short of it with a strip of background showing.
        val bleed = 26.dp

        // Proportional rather than fixed, and deliberately shallow: this is a
        // greeting, and a greeting that pushes the music off the screen has
        // its priorities the wrong way round. Bounded at both ends so it can't
        // become a stripe on a wide window or a wall on a narrow one.
        val height = (pane * 0.17f).coerceIn(178.dp, 236.dp)

        Box(
            Modifier
                .offset(x = -bleed)
                .width(pane + bleed * 2)
                .height(height),
        ) {
            // The picture goes down first and everything else is laid over the
            // whole hero, not over the picture. Fading the image to the page
            // colour while a wash sat behind it at a different colour was what
            // put a seam down the middle: two gradients meeting at an edge
            // instead of one continuous surface.
            // The name, set enormous and left almost invisible, filling the
            // space between the words and the figure. It reads as texture from
            // across the room and as the word itself when you look at it — and
            // it goes down before the picture, so she stands in front of it.
            Text(
                "Blazify",
                color = Blz.ink.copy(alpha = if (dark) 0.055f else 0.045f),
                fontSize = (pane.value * 0.13f).coerceIn(96f, 210f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-4).sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = pane * 0.02f),
            )

            hero?.let {
                Image(
                    it,
                    contentDescription = null,
                    // Fitted rather than cropped now she's small: cropping a
                    // narrow box takes the sides off the figure instead of
                    // making her smaller, which is the opposite of the ask.
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Flush with the right edge — an inset here left a strip
                        // of bare background beside her that read as a gap.
                        // Lifted a little off the bottom so she isn't standing
                        // on the shelves below.
                        .padding(bottom = 22.dp)
                        .width(pane * 0.24f)
                        .fillMaxHeight(0.86f),
                )
            }

            // Across the whole width: solid where the words are, gone by the
            // far edge, so the picture emerges rather than starting.
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to Blz.page,
                        0.46f to Blz.page,
                        0.68f to Blz.page.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ),
            )

            // A breath of the accent, gone well before the bottom. Vertical
            // rather than diagonal, because a diagonal one is still part-way
            // through its colour when it reaches the bottom edge — and a wash
            // that stops mid-colour is precisely what draws a line across the
            // page and turns one screen into two.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Blaze.Amber.copy(alpha = if (dark) 0.20f else 0.14f),
                        0.45f to Blaze.Ember.copy(alpha = if (dark) 0.07f else 0.05f),
                        0.80f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
            )

            // The last thing down, so nothing above can put its own edge back:
            // whatever is left of the picture and the wash is taken to the page
            // colour by the bottom, and the shelves begin on the same surface.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        0.86f to Blz.page.copy(alpha = 0.72f),
                        1f to Blz.page,
                    ),
                ),
            )

            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = bleed + 8.dp, end = 24.dp)
                    .widthIn(max = pane * 0.52f),
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
                    // Sized off the window, so it stays the largest thing on the
                    // screen without running off a small one.
                    fontSize = (pane.value * 0.022f).coerceIn(26f, 36f).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (pane.value * 0.025f).coerceIn(30f, 40f).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Pick up where you left off, or let it choose for you.",
                    color = Blz.muted,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Row(
                    Modifier.padding(top = 16.dp),
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
            .padding(start = 18.dp, end = 24.dp, top = 11.dp, bottom = 11.dp),
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
