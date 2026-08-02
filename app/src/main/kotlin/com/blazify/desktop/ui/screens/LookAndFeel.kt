package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.ui.Accent
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.data.Romanize
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.Destination
import com.blazify.desktop.ui.GridSize
import com.blazify.desktop.ui.Look
import com.blazify.desktop.ui.LyricsAlign
import com.blazify.desktop.ui.LyricsSize
import com.blazify.desktop.ui.PlayerBackground
import com.blazify.desktop.ui.ScrubBar
import com.blazify.desktop.ui.SliderStyle
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everything about how the app looks, in one place, with the app itself as the
 * preview.
 *
 * Nothing here opens a sample window or a mock-up: the accent repaints this
 * screen as you pick it, the slider below the choices is a real one, and the
 * shelf preview is drawn at exactly the size a shelf will be. A setting whose
 * effect you have to go and look for is a setting people change once and never
 * touch again.
 */
@Composable
fun LookAndFeelSection(section: @Composable (String, @Composable () -> Unit) -> Unit) {
    section("Accent") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Accent.offered.forEach { accent ->
                    val on = accent == Look.picked && !Look.dynamicColour
                    val (source, hovered) = rememberHovered()
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent.start, accent.end)))
                            .then(
                                if (on) Modifier.border(2.dp, Blz.ink, CircleShape)
                                else Modifier.hoverBackground(Blz.hover, hovered, source),
                            )
                            .clickable { Look.chooseAccent(accent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) {
                            Icon(Icons.Rounded.Check, accent.label, Modifier.size(18.dp), tint = accent.ink)
                        }
                    }
                }
            }
            Switch("Take the colour from the artwork", Look.dynamicColour, Look::chooseDynamicColour)
            Switch("Colour the whole window, not just the accent", Look.tintedWindow, Look::chooseTintedWindow)
            Text(
                if (Look.dynamicColour) "Following the cover of whatever is playing"
                else "${Look.picked.label} — used for whatever is playing, and nothing else",
                color = Blz.dim, fontSize = 11.5.sp,
            )
        }
    }

    section("The bar you drag") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Choices(
                SliderStyle.entries.map { it.label },
                Look.sliderStyle.label,
            ) { picked -> Look.chooseSliderStyle(SliderStyle.entries.first { it.label == picked }) }

            // A real one, at the real size, three-fifths through — the only
            // honest way to show what a shape choice actually looks like.
            ScrubBar(0.6f, {}, Modifier.fillMaxWidth(), thickness = 5.dp)
        }
    }

    section("The full player") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Choices(
                PlayerBackground.entries.map { it.label },
                Look.playerBackground.label,
            ) { picked ->
                Look.choosePlayerBackground(PlayerBackground.entries.first { it.label == picked })
            }
            PlayerPreview()
        }
    }

    section("Shelves") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Choices(
                GridSize.entries.map { it.label },
                Look.gridSize.label,
            ) { picked -> Look.chooseGridSize(GridSize.entries.first { it.label == picked }) }
            ShelfPreview()
        }
    }

    section("When it opens") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Choices(
                listOf(Destination.Home, Destination.Explore, Destination.Library).map { it.label },
                Look.startTab.label,
            ) { picked ->
                Destination.entries.firstOrNull { it.label == picked }?.let(Look::chooseStartTab)
            }
            Switch("Pure black", Look.pureBlack, Look::choosePureBlack)
            Switch("Greeting on the home screen", Look.showGreeting, Look::chooseShowGreeting)
            Reset()
        }
    }
}

/** The lyrics settings, which live on their own page. */
@Composable
fun LyricsSettingsSection(section: @Composable (String, @Composable () -> Unit) -> Unit) {
    section("How they read") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Alignment", color = Blz.ink, fontSize = 13.5.sp)
            Choices(
                LyricsAlign.entries.map { it.label },
                Look.lyricsAlign.label,
            ) { picked -> Look.chooseLyricsAlign(LyricsAlign.entries.first { it.label == picked }) }

            Text("Size", color = Blz.ink, fontSize = 13.5.sp)
            Choices(
                LyricsSize.entries.map { it.label },
                Look.lyricsSize.label,
            ) { picked -> Look.chooseLyricsSize(LyricsSize.entries.first { it.label == picked }) }

            Text(
                "Space between lines — ${Look.lyricsSpacing}",
                color = Blz.ink, fontSize = 13.5.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 4, 7, 12, 18, 26).forEach { gap ->
                    Choices(listOf("$gap"), "${Look.lyricsSpacing}") {
                        Look.chooseLyricsSpacing(gap)
                    }
                }
            }

            LyricsPreview()
        }
    }

    section("Reading them") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(
                "Show them in the Latin alphabet",
                Look.romanize,
                Look::chooseRomanize,
            )
            Text(
                "Written out in Latin letters — for singing along to a language you can " +
                    "hear but not read. Songs already in Latin are left alone.",
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )

            if (Look.romanize) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "LANGUAGES", color = Blz.dim, fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    val all = Romanize.Script.entries.map { it.label }.toSet()
                    Reset(if (Look.romanized.size == all.size) "None" else "All") {
                        Look.chooseRomanized(if (Look.romanized.size == all.size) emptySet() else all)
                    }
                }
                // One at a time, because reading one of these is no reason to
                // have the others rewritten.
                Romanize.Script.entries.forEach { script ->
                    Switch(script.label, script.label in Look.romanized) { on ->
                        Look.chooseRomanized(
                            if (on) Look.romanized + script.label else Look.romanized - script.label,
                        )
                    }
                }
            }

            Switch("Follow along on its own", Look.lyricsFollow, Look::chooseLyricsFollow)
            Switch("Light up the line being sung", Look.lyricsGlow, Look::chooseLyricsGlow)

            Text(
                "Read ahead by %.2fs".format(Look.lyricsLead),
                color = Blz.ink, fontSize = 13.5.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0f, 0.25f, 0.45f, 0.7f, 1f).forEach { lead ->
                    Choices(listOf("%.2f".format(lead)), "%.2f".format(Look.lyricsLead)) {
                        Look.chooseLyricsLead(lead)
                    }
                }
            }
            Text(
                "Sound leaves the player before it leaves the speakers. Nudge this up if " +
                    "the words still land after they're sung, down if they run ahead.",
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun PlayerPreview() {
    val background = when (Look.playerBackground) {
        PlayerBackground.FollowTheme -> Modifier.background(Blz.page)
        PlayerBackground.PureBlack -> Modifier.background(androidx.compose.ui.graphics.Color.Black)
        PlayerBackground.Gradient -> Modifier.background(
            Brush.verticalGradient(
                listOf(Blz.page, Blaze.Amber.copy(alpha = 0.10f), Blaze.Ember.copy(alpha = 0.22f)),
            ),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Blz.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, Modifier.size(20.dp), tint = Blz.dim)
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(19.dp), tint = Blaze.OnAmber)
        }
    }
}

@Composable
private fun LyricsPreview() {
    val align = when (Look.lyricsAlign) {
        LyricsAlign.Left -> androidx.compose.ui.text.style.TextAlign.Start
        LyricsAlign.Centre -> androidx.compose.ui.text.style.TextAlign.Center
        LyricsAlign.Right -> androidx.compose.ui.text.style.TextAlign.End
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Blz.surfaceHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "And the words go by", color = Blz.dim, fontSize = 15.sp,
            textAlign = align, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "one line at a time", color = Blz.ink, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, textAlign = align, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "until the song ends", color = Blz.dim, fontSize = 15.sp,
            textAlign = align, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ShelfPreview() {
    val side = Look.gridSize.art.dp
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(side).clip(RoundedCornerShape(12.dp)).background(Blz.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(22.dp), tint = Blz.dim)
                }
                Box(Modifier.width(side * 0.7f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(Blz.surfaceHigh))
                Box(Modifier.width(side * 0.45f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Blz.surfaceHigh))
            }
        }
    }
}

@Composable
private fun Choices(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == selected
            val (source, hovered) = rememberHovered()
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                        else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
                    )
                    .then(
                        if (on) Modifier.hoverGlow(hovered, source)
                        else Modifier.hoverBackground(Blz.hover, hovered, source),
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            ) {
                Text(
                    option,
                    color = if (on) Blaze.OnAmber else Blz.muted,
                    fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun Switch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onChange(!on) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Blz.ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(38.dp)
                .height(21.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                    else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
                ),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (on) Blaze.OnAmber else Blz.muted),
            )
        }
    }
}

@Composable
private fun Reset(label: String = "Back to defaults", onClick: () -> Unit = { Look.reset() }) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Blz.surfaceHigh)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(label, color = Blz.muted, fontSize = 12.5.sp)
    }
}
