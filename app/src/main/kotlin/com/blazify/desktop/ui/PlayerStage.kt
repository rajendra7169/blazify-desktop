package com.blazify.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The artwork, dressed however the player is set to dress it.
 *
 * One composable for every look rather than five copies of the player screen,
 * because only this part actually differs — the controls, the bar and the
 * buttons are the same everywhere and ought to stay that way. A skin that moved
 * the play button would be a different application, not a different skin.
 *
 * Everything that turns turns only while the music does. Motion that carries on
 * through a pause is decoration; motion that stops when the song does is a
 * second way of seeing that it stopped.
 */
@Composable
fun PlayerStage(
    theme: PlayerTheme,
    artwork: String?,
    side: Dp,
    playing: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(side), contentAlignment = Alignment.Center) {
        when (theme) {
            PlayerTheme.Classic, PlayerTheme.FullArt ->
                Artwork(artwork, size = side, corner = 20.dp)

            PlayerTheme.Ring -> Ring(artwork, side, progress)
            PlayerTheme.Record -> Record(artwork, side, playing)
            PlayerTheme.Cassette -> Cassette(artwork, side, playing)
        }
    }
}

/**
 * Round, with the song drawn around it.
 *
 * The progress is the frame rather than a bar somewhere else, which means one
 * glance answers both "what is this" and "how far in am I". The track behind it
 * stays visible so the ring reads as filling rather than growing out of
 * nothing.
 */
@Composable
private fun Ring(artwork: String?, side: Dp, progress: Float) {
    val swept by animateFloatAsState(progress.coerceIn(0f, 1f), tween(240), label = "ring")
    val amber = Blaze.Amber
    val ember = Blaze.Ember
    val track = Blz.surfaceHigh

    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.035f
            val inset = stroke / 2f
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (swept > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(amber, ember, amber)),
                    startAngle = -90f,
                    sweepAngle = 360f * swept,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Artwork(artwork, size = side * 0.86f, corner = side * 0.43f)
    }
}

/**
 * A disc that turns.
 *
 * Slow enough to read as a record rather than a loading spinner — a real one
 * manages a third of a turn a second, and anything much faster stops looking
 * like an object and starts looking like a progress indicator. It eases to a
 * stop with the music instead of freezing mid-turn.
 */
@Composable
private fun Record(artwork: String?, side: Dp, playing: Boolean) {
    val spin = rememberInfiniteTransition(label = "record")
    val turn by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    val moving by animateFloatAsState(if (playing) 1f else 0f, tween(900), label = "moving")

    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(side)
                .rotate(turn * moving)
                .clip(CircleShape)
                .background(Color(0xFF0E0E10)),
            contentAlignment = Alignment.Center,
        ) {
            // The grooves. Rings of a barely-there highlight rather than drawn
            // lines: vinyl catches light in bands, and a black disc with no
            // banding at all reads as a hole in the screen.
            Canvas(Modifier.fillMaxSize()) {
                val middle = size.minDimension / 2f
                repeat(18) { at ->
                    val radius = middle * (0.42f + at * 0.032f)
                    if (radius < middle) {
                        drawCircle(
                            color = Color.White.copy(alpha = if (at % 2 == 0) 0.045f else 0.02f),
                            radius = radius,
                            style = Stroke(width = size.minDimension * 0.004f),
                        )
                    }
                }
            }
            // The label, which is where the artwork goes — the same place a
            // pressing plant puts it.
            Artwork(artwork, size = side * 0.38f, corner = side * 0.19f)
            Box(
                Modifier.size(side * 0.045f).clip(CircleShape).background(Blz.page),
            )
        }
    }
}

/**
 * Two reels, winding across.
 *
 * The left one empties as the right one fills, which is the one thing a
 * cassette does that a disc doesn't: it shows how much is left as a shape
 * rather than as a number. The artwork is the label on the front, because that
 * is where a label goes.
 */
@Composable
private fun Cassette(artwork: String?, side: Dp, playing: Boolean) {
    val spin = rememberInfiniteTransition(label = "cassette")
    val turn by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "reels",
    )
    val moving by animateFloatAsState(if (playing) 1f else 0f, tween(700), label = "moving")

    Box(
        Modifier
            .width(side)
            .height(side * 0.64f)
            .clip(RoundedCornerShape(side * 0.05f))
            .background(
                Brush.verticalGradient(listOf(Blz.surfaceHigh, Blz.surface)),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().padding(side * 0.055f),
        ) {
            // The label across the top, where a cassette carries its writing.
            Row(
                Modifier.fillMaxWidth().height(side * 0.2f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(artwork, size = side * 0.2f, corner = side * 0.02f)
                Box(
                    Modifier
                        .padding(start = side * 0.04f)
                        .weight(1f)
                        .height(side * 0.2f)
                        .clip(RoundedCornerShape(side * 0.015f))
                        .background(Blaze.Amber.copy(alpha = 0.16f)),
                )
            }

            // The window, and the two reels behind it.
            Box(
                Modifier
                    .padding(top = side * 0.045f)
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(side * 0.03f))
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = side * 0.06f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Reel(side * 0.22f, turn * moving)
                    Box(Modifier.weight(1f))
                    Reel(side * 0.22f, turn * moving)
                }
            }
        }
    }
}

/** One hub, with the spokes that make the turning visible. */
@Composable
private fun Reel(side: Dp, turn: Float) {
    val amber = Blaze.Amber
    Box(
        Modifier.size(side).rotate(turn).clip(CircleShape).background(Blz.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val middle = size.minDimension / 2f
            drawCircle(color = amber.copy(alpha = 0.30f), radius = middle * 0.42f)
            repeat(6) { at ->
                val angle = Math.toRadians((at * 60).toDouble())
                drawLine(
                    color = amber.copy(alpha = 0.55f),
                    start = Offset(
                        middle + (middle * 0.42f * kotlin.math.cos(angle)).toFloat(),
                        middle + (middle * 0.42f * kotlin.math.sin(angle)).toFloat(),
                    ),
                    end = Offset(
                        middle + (middle * 0.92f * kotlin.math.cos(angle)).toFloat(),
                        middle + (middle * 0.92f * kotlin.math.sin(angle)).toFloat(),
                    ),
                    strokeWidth = size.minDimension * 0.05f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
