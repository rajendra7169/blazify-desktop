package com.blazify.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The Blazify mark, read once from the packaged resources.
 *
 * Decoded through Skia rather than the resource helpers so this works whether
 * the app is running from Gradle or from an installed package — the two resolve
 * resources differently, and a logo that only appears in development is worse
 * than no logo.
 */
private val painter: Painter? by lazy {
    runCatching {
        val bytes = object {}.javaClass.getResourceAsStream("/icons/blazify.png")!!.readBytes()
        BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    }.getOrNull()
}

@Composable
fun BlazifyLogo(size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val p = remember { painter }
    if (p != null) {
        Image(p, contentDescription = "Blazify", modifier = modifier.size(size))
    } else {
        FlameChip(size, modifier)
    }
}

/** Drawn if the mark can't be read, so there's never an empty square. */
@Composable
private fun FlameChip(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.LocalFireDepartment, null,
            Modifier.size(size * 0.58f), tint = Blaze.OnAmber,
        )
    }
}
