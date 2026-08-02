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
import androidx.compose.foundation.layout.width
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
import com.blazify.desktop.SleepTimer

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Setting the sleep timer.
 *
 * The readout is the biggest thing on it, because once one is running that's
 * the only reason anyone opens this again. Whether it's counting down or
 * waiting for the song to end changes what the buttons offer — a running timer
 * needs stopping, not setting.
 */
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            // The scrim is the way out: clicking beside a dialog to dismiss it
            // is a habit older than any of this.
            .background(Blaze.OnAmber.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(330.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                // Swallows the click so it doesn't reach the scrim behind.
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Sleep timer", color = Blz.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                SleepTimer.readout,
                color = if (SleepTimer.running) Blaze.Amber else Blz.ink,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )

            if (!SleepTimer.running) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        Chip("${minutes}m") { SleepTimer.start(minutes) }
                    }
                }
                Chip("End of song", wide = true) { SleepTimer.endOfTrack() }
            } else {
                Text(
                    if (SleepTimer.atEndOfTrack) "The music stops when this song ends"
                    else "The music pauses when this runs out",
                    color = Blz.dim, fontSize = 12.sp,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (SleepTimer.running) {
                    Pill("Stop timer", filled = true, Modifier.weight(1f)) {
                        SleepTimer.cancel()
                    }
                }
                Pill("Close", filled = !SleepTimer.running, Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun Chip(label: String, wide: Boolean = false, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .then(if (wide) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(999.dp))
            .background(Blz.surfaceHigh)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Blz.ink, fontSize = 13.sp)
    }
}

@Composable
private fun Pill(
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
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
