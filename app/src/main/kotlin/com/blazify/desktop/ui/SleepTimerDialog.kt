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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.SleepTimer
import kotlin.math.roundToInt

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
    var minutes by remember { mutableStateOf(30) }
    var songs by remember { mutableStateOf(3) }

    Box(
        Modifier
            .fillMaxSize()
            // The scrim is the way out: clicking beside a dialog to dismiss it
            // is a habit older than any of this.
            .background(Blaze.Scrim)
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

            if (SleepTimer.running) {
                if (SleepTimer.mode == SleepTimer.Mode.Clock) {
                    // How much is left, as a shape. A number tells you the
                    // time; a bar tells you how far through it you are without
                    // reading anything.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Blz.surfaceHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(SleepTimer.progress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                        )
                    }
                }
                Text(
                    when (SleepTimer.mode) {
                        SleepTimer.Mode.EndOfTrack -> "The music stops when this song ends"
                        SleepTimer.Mode.Songs -> "The music stops after that many more"
                        else -> "The music fades out when this runs out"
                    },
                    color = Blz.dim, fontSize = 12.sp,
                )
            } else {
                // The dial. Chips cover the four times anyone asks for; the bar
                // is there for the fifth.
                Text(
                    "%d minutes".format(minutes),
                    color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Dial(minutes, 5f, 120f) { minutes = it }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { quick ->
                        Chip("${quick}m", on = minutes == quick) { minutes = quick }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Start", filled = true, Modifier.weight(1f)) {
                        SleepTimer.startClock(minutes)
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Blz.line))

                Chip("End of this song", wide = true) { SleepTimer.endOfTrack() }

                // Counted in songs rather than minutes, because "a few more"
                // is how anyone actually decides this, and no clock says it.
                Text(
                    if (songs == 1) "After 1 more song" else "After $songs more songs",
                    color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Dial(songs, 1f, 20f) { songs = it }
                Chip("Stop after $songs", wide = true) { SleepTimer.afterSongs(songs) }

                Toggle("Fade out at the end", SleepTimer.fade) { SleepTimer.chooseFade(it) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (SleepTimer.running) {
                    Pill("Stop timer", filled = true, Modifier.weight(1f)) { SleepTimer.cancel() }
                }
                Pill("Close", filled = false, Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

/**
 * A bar you drag to choose a number.
 *
 * Whole numbers only — half a minute is not a thing anyone means to ask for,
 * and a slider that lands on 37.4 makes the readout above it look broken.
 */
@Composable
private fun Dial(value: Int, from: Float, to: Float, onChange: (Int) -> Unit) {
    var width by remember { mutableStateOf(1) }
    val fraction = ((value - from) / (to - from)).coerceIn(0f, 1f)

    fun pick(x: Float) {
        val portion = (x / width).coerceIn(0f, 1f)
        onChange((from + portion * (to - from)).roundToInt())
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { pick(it.x) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position.x) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(Blz.surfaceHigh))
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
        )
        Box(Modifier.fillMaxWidth(fraction)) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Blz.ink),
            )
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Blz.muted, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(36.dp)
                .height(20.dp)
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
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (on) Blaze.OnAmber else Blz.muted),
            )
        }
    }
}

@Composable
private fun Chip(label: String, wide: Boolean = false, on: Boolean = false, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .then(if (wide) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (on) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surfaceHigh),
            )
            .then(
                if (on) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Blaze.OnAmber else Blz.ink, fontSize = 13.sp)
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
