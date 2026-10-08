package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Blocked
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The artists kept out, and the way to let each back in.
 *
 * Blocking happens wherever somebody is when they decide it — a song's menu,
 * an artist's page — but undoing it a month later has to happen somewhere you
 * can find without remembering a song of theirs. This is that place.
 */
@Composable
fun BlockedArtistsSection(
    section: @Composable (String, (() -> Unit)?, @Composable () -> Unit) -> Unit,
) {
    section("Artists you don't play", null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Kept out of Home, radio, what plays on after a queue, quick picks and search. " +
                    "Their albums still play if you open one yourself.",
                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
            )
            if (Blocked.all.isEmpty()) {
                Text(
                    "Nobody yet. Choose Don't play from a song's menu or an artist's page.",
                    color = Blz.muted, fontSize = 13.sp,
                )
            }
            Blocked.all.forEach { artist ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        artist.name, color = Blz.ink, fontSize = 13.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val (source, hovered) = rememberHovered()
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Blz.surfaceHigh)
                            .hoverBackground(Blz.hover, hovered, source)
                            .clickable { Blocked.unblock(artist.name, artist.id) }
                            .padding(horizontal = 15.dp, vertical = 7.dp),
                    ) {
                        Text("Unblock", color = Blz.muted, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
