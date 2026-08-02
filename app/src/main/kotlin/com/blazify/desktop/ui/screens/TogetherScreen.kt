package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.Typing
import com.blazify.desktop.together.Together
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.EmptyState
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Listening with other people.
 *
 * Two states, and the screen is built as two screens rather than one that
 * changes: before there's a room you are choosing between starting one and
 * joining one, and after there is you are looking at who's here. Trying to be
 * both at once is how a page ends up with a dead form at the top of it.
 */
@Composable
fun TogetherScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Blaze Together", color = Blz.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "One room, one queue, everybody hearing the same second of the same song.",
                color = Blz.muted, fontSize = 13.sp,
            )
        }

        Together.trouble?.let {
            Text(it, color = Blaze.Amber, fontSize = 12.5.sp, lineHeight = 18.sp)
        }

        if (Together.code == null) Doorway() else Room()
    }
}

/** Start one, or go to one. */
@Composable
private fun Doorway() {
    var typed by remember { mutableStateOf("") }
    val dialling = Together.link == Together.Link.Dialling

    Row(
        Modifier.fillMaxWidth().widthIn(max = 820.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.weight(1f)) {
            Text("Start a room", color = Blz.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "You pick what plays and everyone follows. You'll get a four-letter code " +
                    "to pass around.",
                color = Blz.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
            )
            Pill(if (dialling) "Starting…" else "Start", filled = true) { Together.host() }
        }

        Card(Modifier.weight(1f)) {
            Text("Join a room", color = Blz.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Type the code somebody gave you. The host has to let you in.",
                color = Blz.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Blz.surfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (typed.isEmpty()) {
                    Text("CODE", color = Blz.dim, fontSize = 15.sp, letterSpacing = 3.sp)
                }
                BasicTextField(
                    value = typed,
                    // Upper case on the way in, because the code is upper case
                    // and nobody should have to hold shift to be let into a
                    // room.
                    onValueChange = { typed = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    singleLine = true,
                    textStyle = TextStyle(color = Blz.ink, fontSize = 15.sp, letterSpacing = 3.sp),
                    cursorBrush = SolidColor(Blaze.Amber),
                    modifier = Modifier
                        .fillMaxWidth()
                        // The letter shortcuts stand down while this has focus,
                        // or typing a room code would pause the music halfway
                        // through it.
                        .onFocusChanged { Typing.active = it.isFocused },
                )
            }
            Pill(if (dialling) "Knocking…" else "Join", filled = typed.isNotBlank()) {
                if (typed.isNotBlank()) Together.join(typed)
            }
        }
    }
}

/** Who's here, and what they're waiting on. */
@Composable
private fun Room() {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 820.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Text(
                if (Together.hosting) "You're hosting" else "You're listening in",
                color = Blz.dim, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Together.code.orEmpty(),
                    color = Blaze.Amber, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    modifier = Modifier.weight(1f),
                )
                Tap(Icons.Rounded.ContentCopy, "Copy the code") {
                    runCatching {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
                            StringSelection(Together.code.orEmpty()), null,
                        )
                    }
                }
            }
            Text(
                if (Together.hosting) {
                    "Whatever you play, they hear. Pause, skip and seek all carry across."
                } else {
                    "The host is driving. Suggest something from any song's ⋮ menu and they'll " +
                        "see it."
                },
                color = Blz.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
            )
            Pill("Leave", filled = false) { Together.leave() }
        }

        // Only the host is ever asked, so this simply isn't there for anyone
        // else rather than being there and doing nothing.
        if (Together.knocking.isNotEmpty()) {
            Card {
                Text(
                    "AT THE DOOR", color = Blz.dim, fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                )
                Together.knocking.forEach { knock ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(knock.name, color = Blz.ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        Tap(Icons.Rounded.Check, "Let in", Blaze.Amber) { Together.letIn(knock.id) }
                        Tap(Icons.Rounded.Close, "Turn away") { Together.turnAway(knock.id) }
                    }
                }
            }
        }

        if (Together.suggestions.isNotEmpty()) {
            Card {
                Text(
                    "SUGGESTED", color = Blz.dim, fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                )
                Together.suggestions.forEach { suggestion ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Artwork(suggestion.track.thumbnail, size = 38.dp, corner = 6.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                suggestion.track.title, color = Blz.ink, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "from ${suggestion.from}", color = Blz.dim, fontSize = 11.5.sp,
                            )
                        }
                        Tap(Icons.Rounded.Check, "Add it", Blaze.Amber) { Together.accept(suggestion) }
                        Tap(Icons.Rounded.Close, "No thanks") { Together.decline(suggestion) }
                    }
                }
            }
        }

        Card {
            Text(
                "IN THE ROOM · ${Together.listeners.size}", color = Blz.dim, fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            )
            Together.listeners.forEach { listener ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (listener.host) {
                                    Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                                } else {
                                    Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh))
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            listener.name.take(1).uppercase(),
                            color = if (listener.host) Blaze.OnAmber else Blz.muted,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(listener.name, color = Blz.ink, fontSize = 13.5.sp)
                        Text(
                            when {
                                listener.host -> "Host"
                                // Said plainly: somebody whose laptop shut is
                                // still in the room, and "gone" would be wrong.
                                !listener.here -> "Away"
                                else -> "Listening"
                            },
                            color = if (listener.host) Blaze.Amber else Blz.dim, fontSize = 11.5.sp,
                        )
                    }
                    if (Together.hosting && listener.id != Together.me) {
                        Tap(Icons.Rounded.Star, "Make host") { Together.handOver(listener.id) }
                        Tap(Icons.Rounded.Close, "Remove") { Together.remove(listener.id) }
                    }
                }
            }
        }
    }
}

/** The empty version of this screen, before anything has been tried. */
@Composable
fun TogetherEmpty() {
    EmptyState(
        Icons.Rounded.People,
        "Nobody here yet",
        "Start a room and pass the code around, or type someone else's.",
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Blz.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
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
            .padding(horizontal = 24.dp, vertical = 11.dp),
    ) {
        Text(
            label,
            color = if (filled) Blaze.OnAmber else Blz.ink,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Tap(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(17.dp), tint = tint ?: Blz.muted)
    }
}
