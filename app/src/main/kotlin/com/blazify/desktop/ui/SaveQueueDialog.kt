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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.Typing
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Playlists
import com.blazify.desktop.data.Track
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Keeping a queue.
 *
 * A queue is the one list nobody sits down to build — it collects out of what
 * you played, what you added and wherever a radio wandered off to, and it is
 * gone the moment you press play on something else. This is how an evening
 * becomes something you can put on again.
 *
 * It goes onto the account when there is one, so it follows you, and stays here
 * when there isn't. Named after today by default, since that is
 * almost always what it was.
 */
@Composable
fun SaveQueueDialog(queue: List<Track>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Queue · ${LocalDate.now()}") }
    var working by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(Blaze.Scrim).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Keep this queue", color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${queue.size} songs, in the order they're lined up",
                    color = Blz.muted, fontSize = 12.5.sp,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Blz.surfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                if (name.isEmpty()) Text("Name it", color = Blz.dim, fontSize = 13.sp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Blz.ink, fontSize = 13.sp),
                    cursorBrush = SolidColor(Blaze.Amber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { Typing.active = it.isFocused },
                )
            }

            trouble?.let { Text(it, color = Blaze.Amber, fontSize = 11.5.sp, lineHeight = 17.sp) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(if (working) "Keeping…" else "Keep", filled = true, Modifier.weight(1f)) {
                    if (working || name.isBlank()) return@Pill
                    if (!Account.signedIn) {
                        Playlists.create(name, queue)
                        onDismiss()
                        return@Pill
                    }
                    working = true
                    scope.launch {
                        Catalogue.createAccountPlaylist(name)
                            .onSuccess { id ->
                                // In queue order, one at a time — the
                                // catalogue has no way to be handed a list,
                                // and shuffling them in at once is how a
                                // playlist comes out in the wrong order.
                                queue.forEach { Catalogue.addToAccountPlaylist(id, it.id) }
                                onDismiss()
                            }
                            .onFailure {
                                // Kept here instead, rather than lost.
                                Playlists.create(name, queue)
                                onDismiss()
                            }
                    }
                }
                Pill("Cancel", filled = false, Modifier.weight(1f), onClick = onDismiss)
            }

            Text(
                if (Account.signedIn) "Kept on your account, so it follows you."
                else "Kept on this computer. Sign in to have it follow you.",
                color = Blz.dim, fontSize = 11.5.sp,
            )
        }
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
