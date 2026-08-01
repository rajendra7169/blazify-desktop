package com.blazify.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** The brand palette. Amber is reserved for whatever is live right now. */
object Blaze {
    val Amber = Color(0xFFFFA726)
    val Ember = Color(0xFFFF7043)

    /** Near-black rather than pure black: amber vibrates against #000. */
    val Night = Color(0xFF0A0A0B)
    val Surface = Color(0xFF141416)
    val SurfaceHigh = Color(0xFF1C1C20)
    val Line = Color(0xFF26262B)

    val Ink = Color(0xFFF5F3F0)
    val Muted = Color(0xFF8A8580)
    val Dim = Color(0xFF57534E)

    val Day = Color(0xFFFBF9F6)
    val DayInk = Color(0xFF1A1714)
}

private val Dark = darkColorScheme(
    primary = Blaze.Amber,
    onPrimary = Color(0xFF1A1005),
    secondary = Blaze.Ember,
    background = Blaze.Night,
    onBackground = Blaze.Ink,
    surface = Blaze.Surface,
    onSurface = Blaze.Ink,
    surfaceVariant = Blaze.SurfaceHigh,
    onSurfaceVariant = Blaze.Muted,
    outline = Blaze.Line,
)

private val Light = lightColorScheme(
    primary = Blaze.Amber,
    onPrimary = Color(0xFF1A1005),
    secondary = Blaze.Ember,
    background = Blaze.Day,
    onBackground = Blaze.DayInk,
    surface = Color(0xFFFFFFFF),
    onSurface = Blaze.DayInk,
    surfaceVariant = Color(0xFFF1EDE8),
    onSurfaceVariant = Color(0xFF6E655C),
    outline = Color(0xFFE3DCD2),
)

@Composable
fun BlazifyTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
