package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Net
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Something went wrong, and what to do about it.
 *
 * A line of grey text saying a fetch failed is a report, not a repair. The
 * person reading it has exactly one thing they want to do — try again — and
 * every screen was making them find their own way to it: change page and come
 * back, or restart the application.
 *
 * Says which kind of wrong it is, too, because they need different things. A
 * connection that is down will come back on its own and the button is worth
 * pressing in a minute; a catalogue that refused is worth pressing now.
 */
@Composable
fun Trouble(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (suspend () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var trying by remember { mutableStateOf(false) }
    val offline = !Net.online

    Column(
        modifier.widthIn(max = 520.dp).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                if (offline) Icons.Rounded.CloudOff else Icons.Rounded.Refresh,
                null, Modifier.size(17.dp), tint = Blz.muted,
            )
            Text(
                if (offline) "No connection" else message,
                color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            if (offline) {
                "Whatever is on this machine still plays — downloads, what was kept as you " +
                    "listened, and your own files. This page comes back when the network does."
            } else {
                "The catalogue didn't answer. It is usually worth another go."
            },
            color = Blz.muted, fontSize = 12.5.sp, lineHeight = 18.sp,
        )

        onRetry?.let { again ->
            val (source, hovered) = rememberHovered()
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Blz.surface)
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable(enabled = !trying) {
                        trying = true
                        scope.launch {
                            again()
                            trying = false
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp), tint = Blz.ink)
                Text(
                    if (trying) "Trying…" else "Try again",
                    color = Blz.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Why the song didn't start, said where the song was expected.
 *
 * Playback failures were being kept and never shown: the player moved on, the
 * next song was refused in the same breath, and from the outside the whole
 * queue simply flicked past. A line here is the smallest honest version of what
 * happened — above the controls, on the way to the close button, gone the
 * moment something plays.
 */
@Composable
fun PlaybackNotice(message: String, onDismiss: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Blz.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp), tint = Blaze.Amber)
        Text(
            message,
            color = Blz.ink, fontSize = 12.5.sp, lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Close, "Dismiss",
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(999.dp))
                .hoverBackground(Blz.hover, hovered, source)
                .clickable(onClick = onDismiss)
                .padding(5.dp),
            tint = Blz.muted,
        )
    }
}

/** The same thing, centred, for a page with nothing else on it. */
@Composable
fun TroubleAlone(message: String, onRetry: (suspend () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Trouble(message, onRetry = onRetry)
    }
}
