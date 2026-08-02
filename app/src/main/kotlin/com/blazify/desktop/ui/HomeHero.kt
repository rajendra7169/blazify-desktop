package com.blazify.desktop.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    val hero = remember {
        runCatching { useResource("blazify_people.png") { loadImageBitmap(it) } }.getOrNull()
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pane = maxWidth

        // Proportional rather than fixed, and deliberately shallow: this is a
        // greeting, and a greeting that pushes the music off the screen has
        // its priorities the wrong way round. Bounded at both ends so it can't
        // become a stripe on a wide window or a wall on a narrow one.
        val height = (pane * 0.165f).coerceIn(214.dp, 302.dp)

        // No offsets and nothing reaching past anything: the list gives this
        // item the full width, so the hero simply fills it and clips its own
        // overflow. Fighting the parent's margins with negative offsets and
        // required sizes is what put a strip down one side, stretched the
        // picture, and finally let it escape onto the rail.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds(),
        ) {
            // The picture goes down first and everything else is laid over the
            // whole hero, not over the picture. Fading the image to the page
            // colour while a wash sat behind it at a different colour was what
            // put a seam down the middle: two gradients meeting at an edge
            // instead of one continuous surface.

            hero?.let { picture ->
                Canvas(Modifier.matchParentSize()) {
                    // Scaled to cover the hero rather than to match its width.
                    // Sizing off the width alone made the crowd a thin band on
                    // a narrow window and an enormous one on a wide screen —
                    // the same picture, a completely different size, which is
                    // the opposite of responsive. Covering keeps the figures
                    // the same size relative to the hero at every width; what
                    // changes is how many of them fit.
                    val scale = maxOf(
                        size.width / picture.width,
                        size.height / picture.height,
                    )
                    val drawn = IntSize(
                        (picture.width * scale).toInt(),
                        (picture.height * scale).toInt(),
                    )
                    drawImage(
                        image = picture,
                        // Anchored right and top: the words live on the left,
                        // and heads matter more than feet.
                        dstOffset = IntOffset(size.width.toInt() - drawn.width, 0),
                        dstSize = drawn,
                        alpha = if (dark) 0.95f else 0.8f,
                        // The picture is cut out on black. Added to what's
                        // underneath, black contributes nothing and only the
                        // figures land — no rectangle, and it sits correctly on
                        // whatever colour the artwork has tinted the window.
                        blendMode = BlendMode.Screen,
                    )
                }
            }

            // The name straight across, edge to edge, at a size that stops
            // being a word and becomes the surface the rest of it sits on.
            Text(
                "Blazify",
                // Over the picture rather than under it. Underneath, the
                // crowd is drawn by adding light, which lifted the whole area
                // and rubbed the letters out — the word was there and simply
                // could not be seen.
                color = Blz.ink.copy(alpha = if (dark) 0.13f else 0.09f),
                fontSize = (pane.value * 0.155f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-6).sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )

            // Across the whole width: solid where the words are, gone by the
            // far edge, so the picture emerges rather than starting.
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to Blz.page,
                        0.34f to Blz.page,
                        0.58f to Blz.page.copy(alpha = 0.45f),
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
                    .padding(start = 26.dp, end = 24.dp)
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
