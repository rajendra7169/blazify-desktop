package com.blazify.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A screen with nothing on it yet.
 *
 * An empty page is the first thing plenty of people see, and a line of grey
 * text in a corner reads as something having gone wrong. This says what the
 * screen is for and how to put something on it, at a size that matches the fact
 * that it's currently the only thing there.
 *
 * The mark breathes — slowly, a few percent, with a halo drifting behind it.
 * Enough that the page looks alive rather than stalled, slow enough that it
 * never asks to be watched.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    action: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier,
) {
    val breath = rememberInfiniteTransition(label = "empty")

    val swell by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "swell",
    )
    // The halo runs slower than the mark and out of step with it, so the two
    // never line up into a single pulse.
    val halo by breath.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(3800), RepeatMode.Reverse),
        label = "halo",
    )
    val glow by breath.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.07f,
        animationSpec = infiniteRepeatable(tween(3800), RepeatMode.Reverse),
        label = "glow",
    )

    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(168.dp)
                    .scale(halo)
                    .alpha(glow)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Blaze.Amber, Blaze.Ember))),
            )
            Box(
                Modifier
                    .size(104.dp)
                    .scale(swell)
                    .clip(CircleShape)
                    .background(Blz.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(46.dp), tint = Blaze.Amber)
            }
        }

        Text(
            title,
            color = Blz.ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            detail,
            color = Blz.muted,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 380.dp),
        )

        action?.let { (label, onClick) ->
            val (source, hovered) = rememberHovered()
            Row(
                Modifier
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                    .hoverGlow(hovered, source)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = Blaze.OnAmber,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
