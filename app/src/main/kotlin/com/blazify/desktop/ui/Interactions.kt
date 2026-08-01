package com.blazify.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Hover, as one thing rather than twenty copies of the same three lines.
 *
 * A desktop app lives or dies on this: a mouse is always somewhere, and what it
 * is over should say so. Everything here is deliberately small and fast — 120ms,
 * a shade of background, a percent or two of scale. Enough to feel answered,
 * not enough to notice on the hundredth pass.
 */
@Composable
fun rememberHovered(): Pair<MutableInteractionSource, State<Boolean>> {
    val source = remember { MutableInteractionSource() }
    return source to source.collectIsHoveredAsState()
}

/** Tints the background while the pointer is inside. */
fun Modifier.hoverBackground(
    color: Color,
    hovered: State<Boolean>,
    source: MutableInteractionSource,
): Modifier = composed {
    val alpha by animateFloatAsState(if (hovered.value) 1f else 0f, tween(120), label = "hoverFill")
    this
        .hoverable(source)
        .background(color.copy(alpha = color.alpha * alpha))
}

/** A barely-there lift. Used on artwork cards, never on text. */
fun Modifier.hoverLift(hovered: State<Boolean>, to: Float = 1.02f): Modifier = composed {
    val s by animateFloatAsState(if (hovered.value) to else 1f, tween(140), label = "hoverLift")
    scale(s)
}
