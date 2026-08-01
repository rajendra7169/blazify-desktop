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
 * Album art, at the size it will actually be drawn.
 *
 * The catalogue puts a size in the URL, and whatever it happens to be is often
 * far smaller than the space we're giving it — a row thumbnail asking for the
 * default comes back blurry. Rewriting it to the size we need costs nothing and
 * is the difference between sharp and soft.
 */
@Composable
fun Artwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size).clip(shape).background(Blz.surfaceHigh)) {
        if (url == null) {
            Placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url.atSize(size.value.toInt() * 2),
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
private fun String.atSize(pixels: Int): String {
    val capped = pixels.coerceIn(64, 1080)
    return Regex("=w\\d+-h\\d+").replace(this, "=w$capped-h$capped")
}
