package com.blazify.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import com.blazify.desktop.audio.VideoEngine
import com.blazify.desktop.data.Track
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * How the picture gives way to the page beneath it.
 *
 * Clear for the top half, where the picture is the point; most of the way to
 * the page by the last sixth; the page itself at the foot, so the controls
 * below sit on something solid rather than on a bright frame that changes
 * every second.
 */
fun videoFade(page: Color): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.5f to Color.Transparent,
    0.85f to page.copy(alpha = 0.85f),
    1f to page,
)

/**
 * The song's music video, filling the box it is given.
 *
 * The cover stands in until the video has a picture ready, and the video then
 * fades in over it — never a black box, never a flash of the wrong size,
 * because the bands were measured and trimmed before it was shown at all.
 *
 * The video lives exactly as long as this does: put the look away, or close
 * the player, and the stream is stopped and given back. It also waits while
 * the window is down in the taskbar or swapped for the small one, since
 * nobody is watching either.
 */
@Composable
fun MusicVideoStage(track: Track?, page: Color, modifier: Modifier = Modifier) {
    LaunchedEffect(track?.id) {
        if (track != null) VideoEngine.show(track) else VideoEngine.hide()
    }
    val unseen = WindowMode.mini || WindowMode.minimised
    LaunchedEffect(unseen) { VideoEngine.hold(unseen) }
    DisposableEffect(Unit) { onDispose { VideoEngine.hide() } }

    val ready = VideoEngine.revealed && track != null && VideoEngine.showingFor == track.id
    val shown by animateFloatAsState(if (ready) 1f else 0f, tween(600), label = "videoIn")

    Box(modifier.clipToBounds()) {
        Backdrop(track?.thumbnail, Modifier.fillMaxSize())
        if (shown > 0f) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = shown }) {
                // Read here, in the drawing, so a new frame redraws the picture
                // without asking the whole player to be laid out again.
                VideoEngine.frames
                val frame = VideoEngine.snapshot() ?: return@Canvas
                val wide = VideoEngine.pictureWidth.takeIf { it in 1..frame.width } ?: frame.width
                val tall = VideoEngine.pictureHeight.takeIf { it in 1..frame.height } ?: frame.height
                val cut = VideoEngine.trimmed.coerceIn(0, tall / 3)

                // Cropped to fill, from the part of the frame that is picture.
                // Whichever way the box is the wrong shape, the excess is cut
                // equally off both sides rather than squashed to fit.
                val pictureTall = (tall - 2 * cut).toFloat()
                val scale = maxOf(size.width / wide, size.height / pictureTall)
                val takeWide = size.width / scale
                val takeTall = size.height / scale
                val left = (wide - takeWide) / 2f
                val top = cut + (pictureTall - takeTall) / 2f
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawImageRect(
                        frame,
                        Rect.makeXYWH(left, top, takeWide, takeTall),
                        Rect.makeWH(size.width, size.height),
                        FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                        Paint(),
                        true,
                    )
                }
                frame.close()
            }
        }
        Box(Modifier.fillMaxSize().background(videoFade(page)))
    }
}
