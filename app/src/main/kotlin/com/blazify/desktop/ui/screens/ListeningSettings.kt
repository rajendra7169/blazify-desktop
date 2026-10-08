package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Listening
import com.blazify.desktop.data.SponsorBlock
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * How songs are heard while they play: their speed and their key.
 *
 * On the player page beside the rest of playback, in sections of its own,
 * because each of these changes the song itself rather than what happens
 * around it.
 */
@Composable
fun ListeningSettingsSection(
    section: @Composable (String, (() -> Unit)?, @Composable () -> Unit) -> Unit,
) {
    section("Speed", { Listening.reset(); PlayerState.chooseMusicSpeed(1f) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "For songs. Talk keeps its own speed, chosen beside the episode — an hour of " +
                    "somebody explaining something at one and a half is not a reason to hear " +
                    "an album that way.",
                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
            )
            Pills(
                Listening.speeds.map(::times),
                times(Listening.speed),
            ) { picked -> PlayerState.chooseMusicSpeed(Listening.speeds.first { times(it) == picked }) }

            Toggle("Keep the pitch natural", Listening.naturalPitch, Listening::chooseNaturalPitch)
            Text(
                buildString {
                    append(
                        if (Listening.naturalPitch) {
                            "A faster song stays in its own key. "
                        } else {
                            "Like a record player: faster is higher, slower is lower. "
                        },
                    )
                    append(
                        if (Listening.pitchWaitsForRestart) {
                            "Takes effect next time Blazify starts — the player is told this " +
                                "once, when it starts."
                        } else {
                            "A change takes effect next time Blazify starts."
                        },
                    )
                },
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )
        }
    }

    SkippingSettingsSection(section)
}

/**
 * Which marked stretches of a video are jumped over.
 *
 * Its own section, because it is the one setting here that asks somebody
 * else something — and saying what is asked, and how, is part of the setting.
 */
@Composable
fun SkippingSettingsSection(
    section: @Composable (String, (() -> Unit)?, @Composable () -> Unit) -> Unit,
) {
    section("Skip the talking in music videos", null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "A music video often opens with a minute of scene and closes with a plug for " +
                    "the channel. Viewers mark those stretches in the public SponsorBlock " +
                    "database, and the ones chosen here are jumped over as they come. Asked " +
                    "by a scrambled piece of the song's id, so the database never learns " +
                    "what is playing; when it can't be reached, the song simply plays whole.",
                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
            )
            SponsorBlock.Kind.entries.forEach { kind ->
                Toggle(kind.label, kind in Listening.skipKinds) { Listening.chooseSkip(kind, it) }
            }
        }
    }
}

/** A speed as it is said: one and a half times, not one point five zero. */
private fun times(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}×" else "$value×"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                    .padding(horizontal = 13.dp, vertical = 7.dp),
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
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
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
