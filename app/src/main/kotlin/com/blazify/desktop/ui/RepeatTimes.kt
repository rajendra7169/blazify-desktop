package com.blazify.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The repeat button's second button.
 *
 * A left click goes round the modes, as it always has; a right click offers a
 * count for the song that is playing. Two buttons on one control rather than a
 * fourth mode in the cycle, because "five times" is not a mode anybody wants to
 * click past on the way back to off.
 */
@Composable
fun RepeatTimesArea(content: @Composable () -> Unit) {
    ContextMenuArea(
        items = {
            val track = PlayerState.current ?: return@ContextMenuArea emptyList()
            buildList {
                PlayerState.timesChoices.forEach { times ->
                    add(ContextMenuItem("Play this $times times, then carry on") {
                        PlayerState.playTimes(track, times)
                    })
                }
                if (PlayerState.timesLeft > 0) {
                    add(ContextMenuItem("Stop counting") { PlayerState.stopTimes() })
                }
            }
        },
        content = content,
    )
}

/**
 * How many more times, on the corner of the repeat button.
 *
 * The plays still to come rather than the number asked for: a badge that says
 * three while the third one is playing is a badge you have to do sums with.
 */
@Composable
fun BoxScope.RepeatTimesBadge(size: Dp = 15.dp) {
    val left = PlayerState.timesLeft
    if (left <= 0) return
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(1.dp)
            .size(size)
            .clip(CircleShape)
            .background(Blaze.Amber),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (left > 9) "9+" else "$left",
            color = Blaze.OnAmber,
            fontSize = (size.value * 0.6f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
