package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Album art, at the size and shape it will actually be drawn.
 *
 * The catalogue puts a size in the URL, and whatever it happens to be is often
 * far smaller than the space we're giving it — a row thumbnail asking for the
 * default comes back blurry. Rewriting it to the size we need costs nothing and
 * is the difference between sharp and soft. Asking in the right *shape* matters
 * just as much: request a square of a widescreen still and it comes back
 * already cropped, with the sides gone before we ever see it.
 */
@Composable
fun Artwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
    height: Dp = size,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size, height).clip(shape).background(Blz.surfaceHigh)) {
        if (url == null) {
            Placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url.atSize(size.value.toInt() * 2, height.value.toInt() * 2),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state.value) {
                    // A skeleton would flash on a cached hit; the surface behind
                    // is already the right shape, so leaving it bare is calmer.
                    is AsyncImagePainter.State.Error -> Placeholder()
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp), tint = Blz.dim)
    }
}

/** Rewrite the size the catalogue baked into the URL. Left alone if absent. */
private fun String.atSize(width: Int, height: Int): String {
    val w = width.coerceIn(64, 1920)
    val h = height.coerceIn(64, 1080)
    return Regex("=w\\d+-h\\d+").replace(this, "=w$w-h$h")
}

/**
 * The cover as a wall rather than a tile.
 *
 * Cropped to fill whatever it is given and asked for at a generous size, since
 * this one is stretched across a window rather than shown at forty pixels.
 * Nothing is drawn when there is no cover — the ground underneath is already
 * the right colour, and a grey rectangle the size of the screen is worse than
 * none.
 */
@Composable
fun Backdrop(url: String?, modifier: Modifier = Modifier) {
    if (url == null) return
    SubcomposeAsyncImage(
        model = url.atSize(1200, 1200),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    ) {
        when (painter.state.value) {
            is AsyncImagePainter.State.Error -> Unit
            else -> SubcomposeAsyncImageContent()
        }
    }
}
