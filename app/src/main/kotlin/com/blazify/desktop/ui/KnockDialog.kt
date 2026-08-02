package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.together.Together

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Somebody is at the door, wherever you happen to be.
 *
 * Shown over the whole window rather than on the Blaze Together page, because
 * the person knocking is sitting somewhere watching a spinner and you are
 * almost certainly not on that page — you are listening to something, which is
 * the entire reason there is a room. A request that can only be seen on one
 * screen is a request nobody answers.
 */
@Composable
fun KnockDialog() {
    val knock = Together.knocking.firstOrNull() ?: return

    Box(
        Modifier.fillMaxSize().background(Blaze.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PersonAdd, null, Modifier.size(26.dp), tint = Blaze.OnAmber)
            }

            Text(
                knock.name, color = Blz.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "wants to listen with you",
                color = Blz.muted, fontSize = 13.sp,
            )

            if (Together.knocking.size > 1) {
                Text(
                    "and ${Together.knocking.size - 1} more waiting",
                    color = Blz.dim, fontSize = 11.5.sp,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice("Not now", filled = false, Modifier.weight(1f)) {
                    Together.turnAway(knock.id)
                }
                Choice("Let them in", filled = true, Modifier.weight(1f)) {
                    Together.letIn(knock.id)
                }
            }

            Text(
                "Settings › Blaze Together can let anyone with the code in without asking.",
                color = Blz.dim, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Choice(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surfaceHigh),
            )
            .then(
                if (filled) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) Blaze.OnAmber else Blz.ink,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}
