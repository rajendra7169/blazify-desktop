package com.blazify.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The accent. Reserved for whatever is live right now — never decoration.
 *
 * Blaze amber is the default and what the app is named for, but the colour is
 * chosen rather than fixed: every screen reads these three, so picking a
 * different accent repaints all of it at once.
 */
object Blaze {
    val Amber: Color get() = Look.accent.start
    val Ember: Color get() = Look.accent.end
    val OnAmber: Color get() = Look.accent.ink

    /** The amber the app is named for, whatever the accent happens to be. */
    val Brand = Color(0xFFFFA726)
}

/** Whether the window follows the desktop, or is pinned to one appearance. */
enum class ThemeMode { System, Dark, Light }

/**
 * The colours Material's own scheme has no name for.
 *
 * The rail and the transport strip are deliberately a shade apart from the page
 * behind them — that separation is what makes the content read as the thing
 * you're looking at, and it needs its own slot in both appearances.
 */
@Immutable
data class BlazeColors(
    val dark: Boolean,
    val page: Color,
    val rail: Color,
    val bar: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val hover: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val dim: Color,
    val skeleton: Color,
    val skeletonSheen: Color,
)

private val NightColors = BlazeColors(
    dark = true,
    // Near-black, not pure black: a saturated accent vibrates against #000.
    page = Color(0xFF0A0A0B),
    rail = Color(0xFF0E0E10),
    bar = Color(0xFF111114),
    surface = Color(0xFF141416),
    surfaceHigh = Color(0xFF1C1C20),
    hover = Color(0xFF1C1C20),
    line = Color(0xFF26262B),
    ink = Color(0xFFF5F3F0),
    muted = Color(0xFF8A8580),
    dim = Color(0xFF57534E),
    skeleton = Color(0xFF1A1A1E),
    skeletonSheen = Color(0xFF2A2A31),
)

private val DayColors = BlazeColors(
    dark = false,
    // Warm off-white rather than pure white, so amber sits on it without glare.
    page = Color(0xFFFBF9F6),
    rail = Color(0xFFF4F0EA),
    bar = Color(0xFFF7F4EF),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF1EDE8),
    hover = Color(0xFFEDE8E1),
    line = Color(0xFFE3DCD2),
    ink = Color(0xFF1A1714),
    muted = Color(0xFF6E655C),
    dim = Color(0xFF9A928A),
    skeleton = Color(0xFFEDE8E1),
    skeletonSheen = Color(0xFFF7F4EF),
)

val LocalBlazeColors = staticCompositionLocalOf { NightColors }

/** Shorthand so screens read `Blz.ink` rather than reaching through the local. */
val Blz: BlazeColors
    @Composable @ReadOnlyComposable get() = LocalBlazeColors.current

private fun schemeFor(c: BlazeColors) = if (c.dark) {
    darkColorScheme(
        primary = Blaze.Amber, onPrimary = Blaze.OnAmber, secondary = Blaze.Ember,
        background = c.page, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.muted, outline = c.line,
    )
} else {
    lightColorScheme(
        primary = Blaze.Amber, onPrimary = Blaze.OnAmber, secondary = Blaze.Ember,
        background = c.page, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.muted, outline = c.line,
    )
}

/**
 * The dark palette taken all the way down.
 *
 * Only the grounds change — the surfaces above them keep their separation, or
 * the rail and the transport strip would dissolve into the page and the layout
 * would lose its shape entirely.
 */
private val BlackColors = NightColors.copy(
    page = Color(0xFF000000),
    rail = Color(0xFF000000),
    bar = Color(0xFF050506),
    surface = Color(0xFF0C0C0E),
    surfaceHigh = Color(0xFF151518),
    hover = Color(0xFF151518),
    line = Color(0xFF1E1E22),
    skeleton = Color(0xFF121215),
    skeletonSheen = Color(0xFF222228),
)

@Composable
fun BlazifyTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = when {
        !dark -> DayColors
        Look.pureBlack -> BlackColors
        else -> NightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBlazeColors provides colors) {
        MaterialTheme(colorScheme = schemeFor(colors), content = content)
    }
}
