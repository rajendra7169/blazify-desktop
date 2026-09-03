package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.StarPrompt

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Asking for a star, the third and last time it will ever be asked.
 *
 * Deliberately plain. This is a favour being asked of somebody who is in the
 * middle of doing something else, so it says what it wants in two lines and
 * offers a way out that means it.
 */
@Composable
fun StarPromptDialog() {
    if (!StarPrompt.showing) return

    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.Scrim)
            // Clicking beside it is Later, which is the kindest default for a
            // question nobody asked to be shown.
            .clickable(onClick = StarPrompt::dismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Getting on with Blazify?",
                color = Blz.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "It is made by one person and given away, with no ads and nothing " +
                    "tracked. A star on GitHub is most of how anyone else finds it.",
                color = Blz.dim,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "No thanks",
                    color = Blz.muted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = StarPrompt::stop)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "Later",
                    color = Blz.muted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = StarPrompt::dismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "Star it",
                    color = Blaze.Amber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = StarPrompt::open)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
