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

/** Every colour on its way to the next one, over the same easing. */
@Composable
private fun BlazeColors.eased(): BlazeColors {
    val spec = androidx.compose.animation.core.tween<Color>(520)

    @Composable
    fun to(target: Color) = androidx.compose.animation.animateColorAsState(target, spec).value

    return copy(
        page = to(page),
        rail = to(rail),
        bar = to(bar),
        surface = to(surface),
        surfaceHigh = to(surfaceHigh),
        hover = to(hover),
        line = to(line),
        ink = to(ink),
        muted = to(muted),
        dim = to(dim),
        skeleton = to(skeleton),
        skeletonSheen = to(skeletonSheen),
    )
}

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

/**
 * The whole window in the colour of what's playing.
 *
 * An accent dropped onto a grey window is decoration. A window built out of the
 * song's own hue is a different thing — ground, rail, panels and accent all
 * belong to one colour, and the app looks like the record rather than like an
 * application that happens to be showing one.
 *
 * Only the hue is taken; everything else is set here. That is deliberate and it
 * is what keeps this restful: a garish cover cannot produce a garish window,
 * because the saturation and brightness of every surface are decided by these
 * numbers and not by the picture. The grounds stay deep and only lightly
 * coloured, the text stays near-white, and the contrast between them never
 * moves whatever is playing.
 */
private fun tinted(base: BlazeColors, accent: Accent): BlazeColors {
    val c = java.awt.Color(accent.head.toInt())
    val hue = java.awt.Color.RGBtoHSB(c.red, c.green, c.blue, null)[0]

    fun shade(saturation: Float, brightness: Float) =
        Color(java.awt.Color.HSBtoRGB(hue, saturation, brightness))

    return if (base.dark) {
        base.copy(
            // Deep and quiet. Enough colour to read as a colour, far short of
            // anything you'd notice for an hour at a time.
            page = shade(0.42f, 0.085f),
            rail = shade(0.40f, 0.105f),
            bar = shade(0.38f, 0.125f),
            surface = shade(0.34f, 0.155f),
            surfaceHigh = shade(0.30f, 0.20f),
            hover = shade(0.28f, 0.235f),
            line = shade(0.24f, 0.29f),
            // Warmed a shade towards the hue rather than left pure white, so
            // the text belongs to the window instead of sitting on top of it —
            // and still reads at full strength against the ground.
            ink = shade(0.04f, 0.97f),
            muted = shade(0.12f, 0.70f),
            dim = shade(0.16f, 0.48f),
            skeleton = shade(0.32f, 0.18f),
            skeletonSheen = shade(0.28f, 0.25f),
        )
    } else {
        base.copy(
            // The same idea inverted: a wash of the hue rather than a flood,
            // and ink dark enough that nothing has to be squinted at.
            page = shade(0.05f, 0.995f),
            rail = shade(0.09f, 0.96f),
            bar = shade(0.08f, 0.975f),
            surface = shade(0.03f, 1f),
            surfaceHigh = shade(0.11f, 0.945f),
            hover = shade(0.14f, 0.915f),
            line = shade(0.17f, 0.87f),
            ink = shade(0.45f, 0.13f),
            muted = shade(0.25f, 0.45f),
            dim = shade(0.18f, 0.63f),
            skeleton = shade(0.11f, 0.93f),
            skeletonSheen = shade(0.06f, 0.98f),
        )
    }
}

@Composable
fun BlazifyTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val base = when {
        !dark -> DayColors
        Look.pureBlack -> BlackColors
        else -> NightColors
    }
    // Pure black is a deliberate choice of no colour at all, so it wins.
    val wanted = ArtworkColour.accent
        ?.takeIf { Look.tintedWindow && Look.dynamicColour && !Look.pureBlack }
        ?.let { tinted(base, it) }
        ?: base

    // Eased between rather than swapped. A window that changes colour the
    // instant a track does is startling; over half a second it reads as the
    // room changing with the music, which is the whole point.
    val colors = wanted.eased()
    androidx.compose.runtime.CompositionLocalProvider(LocalBlazeColors provides colors) {
        MaterialTheme(colorScheme = schemeFor(colors), content = content)
    }
}
