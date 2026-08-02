package com.blazify.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A bar you can drag.
 *
 * While a drag is in progress the bar shows where your finger is, not where
 * playback is — otherwise the handle fights you, snapping back to the old
 * position each time a progress update lands mid-gesture. The real value is
 * only sent when you let go.
 *
 * The handle appears on hover and while dragging. At rest it's a plain two-tone
 * line, which is what a bar that isn't being touched should look like.
 */
/**
 * The played part, drawn as a wave.
 *
 * Only the part behind the handle waves — the track ahead stays a straight
 * line, which is what makes the handle read as the crest rather than as a dot
 * sitting on a decorated bar.
 */
@Composable
private fun Wave(
    fraction: Float,
    colour: Color,
    thickness: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val end = size.width * fraction
        if (end <= 0f) return@Canvas
        val middle = size.height / 2f
        val amplitude = size.height / 3.4f
        val wavelength = 22.dp.toPx()

        val path = Path()
        var x = 0f
        path.moveTo(0f, middle)
        while (x < end) {
            val next = (x + wavelength / 2f).coerceAtMost(end)
            val crest = if ((x / (wavelength / 2f)).toInt() % 2 == 0) -amplitude else amplitude
            path.quadraticTo((x + next) / 2f, middle + crest, next, middle)
            x = next
        }
        drawPath(path, colour, style = Stroke(width = thickness.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun ScrubBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Blaze.Amber,
    track: Color = Blz.surfaceHigh,
    thickness: androidx.compose.ui.unit.Dp = 4.dp,
) {
    // The chosen style decides the shape; the caller still decides the weight,
    // since a volume slider and a seek bar want different presence at the same
    // setting.
    val style = Look.sliderStyle
    val height = when (style) {
        SliderStyle.Slim -> thickness * 0.6f
        SliderStyle.Capsule -> thickness
        SliderStyle.Squiggly -> thickness
    }
    val (source, hovered) = rememberHovered()
    var width by remember { mutableStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val shown = if (dragging) dragFraction else fraction.coerceIn(0f, 1f)
    val handle by animateFloatAsState(
        if (hovered.value || dragging) 1f else 0f, tween(120), label = "handle",
    )

    Box(
        modifier
            // A hair-thin line is hard to hit with a mouse. The padding widens
            // the target without changing how it looks.
            .padding(vertical = 6.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .hoverBackground(Color.Transparent, hovered, source)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragFraction)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track))
        if (style == SliderStyle.Squiggly && shown > 0f) {
            Wave(shown, fill, thickness, Modifier.fillMaxWidth().height(thickness * 3))
        } else {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(fill),
            )
        }
        if (handle > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size((10 * handle).dp)
                        .clip(CircleShape)
                        .background(Blz.ink),
                )
            }
        }
    }
}
