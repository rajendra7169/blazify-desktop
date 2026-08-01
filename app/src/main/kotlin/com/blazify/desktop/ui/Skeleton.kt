package com.blazify.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A placeholder in the shape of whatever is coming.
 *
 * Deliberately the same size and position as the real thing, so nothing jumps
 * when the content lands — that jump is the reason spinners feel worse than
 * they should. The sheen is slow (1.3s) and low-contrast; a fast shimmer on a
 * screen full of them turns into a strobe.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, corner: Dp = 7.dp) {
    val move by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "sheen",
    )
    val c = Blz
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    0f to c.skeleton,
                    move.coerceIn(0.05f, 0.95f) to c.skeletonSheen,
                    1f to c.skeleton,
                ),
            ),
    )
}

/** A shelf of artwork cards, waiting. */
@Composable
fun SkeletonRail(count: Int = 7, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Skeleton(Modifier.width(120.dp).height(15.dp), corner = 4.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(count) {
                Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), corner = 9.dp)
                    Skeleton(Modifier.fillMaxWidth(0.85f).height(11.dp), corner = 3.dp)
                    Skeleton(Modifier.fillMaxWidth(0.55f).height(10.dp), corner = 3.dp)
                }
            }
        }
    }
}

/** A list of track rows, waiting. */
@Composable
fun SkeletonRows(count: Int = 8, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(count) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Skeleton(Modifier.size(38.dp), corner = 6.dp)
                Column(
                    Modifier.fillMaxWidth(0.55f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Skeleton(Modifier.fillMaxWidth(0.7f).height(12.dp), corner = 3.dp)
                    Skeleton(Modifier.fillMaxWidth(0.42f).height(10.dp), corner = 3.dp)
                }
            }
        }
    }
}
