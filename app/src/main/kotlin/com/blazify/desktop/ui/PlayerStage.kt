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
    // The cassette is a landscape object and the rest are square, so the stage
    // takes the width it is given and only fixes the height for the ones that
    // need it. Forcing the tape into a square would crop the shell.
    Box(
        modifier.then(
            if (theme == PlayerTheme.Cassette) Modifier.width(side) else Modifier.size(side),
        ),
        contentAlignment = Alignment.Center,
    ) {
        when (theme) {
            PlayerTheme.Classic, PlayerTheme.FullArt ->
                Artwork(artwork, size = side, corner = 20.dp)

            PlayerTheme.Ring -> Ring(artwork, side, progress)

            // The turntable and the tape are drawn entirely in Canvas, in their
            // own files, at whatever size they are given — the stage only has
            // to hand them the room and get out of the way.
            PlayerTheme.Record -> VinylTurntable(
                thumbnailUrl = artwork,
                isPlaying = playing,
                progress = progress,
                modifier = Modifier.size(side),
            )

            PlayerTheme.Cassette -> CassetteTape(
                isPlaying = playing,
                progress = progress,
                accent = Blaze.Amber,
                thumbnailUrl = artwork,
                modifier = Modifier.width(side),
            )
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
