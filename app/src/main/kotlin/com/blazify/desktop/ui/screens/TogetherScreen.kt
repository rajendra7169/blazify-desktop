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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.blazify.desktop.ui.Navigator
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
 * Laid out the way the phone lays it out, because it is the same feature and a
 * room can hold both: the disc, then where the line stands, then either the
 * form for getting into a room or the room itself, then the way to its
 * settings. Everything down one centred column — this is a page you read top to
 * bottom once and then mostly ignore, not a dashboard.
 *
 * Connecting and joining are two separate acts here, as they are there. The
 * line can be open with nobody in a room, which is what makes "connected" a
 * thing worth saying out loud on its own row.
 */
@Composable
fun TogetherScreen() {
    var name by remember { mutableStateOf(Together.username) }
    var codeTyped by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Hero()

        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LineCard()

            if (Together.link == Together.Link.On && Together.code == null) {
                Text(
                    "The line stays open while this window is. Nothing is shared until " +
                        "you are in a room.",
                    color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }

            if (Together.code == null) {
                Doorway(
                    name = name,
                    onName = { name = it; Together.chooseUsername(it) },
                    code = codeTyped,
                    onCode = { codeTyped = it },
                )
            } else {
                Room()
            }

            SettingsCard()
        }
    }
}

/**
 * The disc, breathing.
 *
 * The same mark the phone opens this screen with — a halo pulsing behind a
 * gradient disc — because somebody who set a room up on their phone should
 * recognise this page before they read a word of it. It is also the honest
 * shape for the feature: something quietly alive, waiting for other people.
 */
@Composable
private fun Hero() {
    val breath = rememberInfiniteTransition(label = "togetherHero")
    val halo by breath.animateFloat(
        initialValue = 1f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse,
        ),
        label = "halo",
    )
    val glow by breath.animateFloat(
        initialValue = 0.22f, targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val disc by breath.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse,
        ),
        label = "disc",
    )

    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(112.dp)
                    .graphicsLayer { scaleX = halo; scaleY = halo }
                    .clip(CircleShape)
                    .background(Blaze.Amber.copy(alpha = glow)),
            )
            Box(
                Modifier
                    .size(88.dp)
                    .graphicsLayer { scaleX = disc; scaleY = disc }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GroupAdd, null, Modifier.size(44.dp), tint = Blaze.OnAmber)
            }
        }
        Text(
            "Blaze Together", color = Blz.ink, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Listen with your friends in real time. Start a room to host, or join one " +
                "with a code.",
            color = Blz.muted, fontSize = 13.sp, lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}

/**
 * Where the line stands, said in one row.
 *
 * A dot and a word, the way the phone says it — and the button underneath is
 * whichever one is possible right now rather than both greyed against each
 * other. Being connected without being in a room is a real state and it needs
 * saying, or "join" failing looks like the code was wrong.
 */
@Composable
private fun LineCard() {
    val link = Together.link
    val colour = when (link) {
        Together.Link.On -> Blaze.Amber
        Together.Link.Dialling -> Blz.ink
        Together.Link.Trouble -> Blaze.Ember
        Together.Link.Off -> Blz.dim
    }

    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colour))
            Text(
                when (link) {
                    Together.Link.On -> "Connected"
                    Together.Link.Dialling -> "Connecting…"
                    Together.Link.Trouble -> "Connection error"
                    Together.Link.Off -> "Disconnected"
                },
                color = colour, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        if (link == Together.Link.Dialling) {
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp))
                    .background(Blz.surfaceHigh),
            ) {
                val pulse = rememberInfiniteTransition(label = "dialling")
                val along by pulse.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
                    label = "along",
                )
                Box(
                    Modifier.fillMaxWidth(0.35f).height(4.dp)
                        .offset(x = (along * 200).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (link) {
                Together.Link.Off, Together.Link.Trouble ->
                    Wide("Connect", filled = true, Modifier.weight(1f)) { Together.connect() }
                Together.Link.On ->
                    Wide("Disconnect", filled = false, Modifier.weight(1f)) { Together.disconnect() }
                Together.Link.Dialling -> Unit
            }
        }
    }
}

/**
 * The name you go by, and the way in.
 *
 * One card rather than two side by side. Both routes need the name, so asking
 * for it once above them is the difference between one form and two that
 * disagree — and which button appears follows what you have typed: no code
 * means you are starting a room, a full code means you are joining one.
 */
@Composable
private fun Doorway(
    name: String,
    onName: (String) -> Unit,
    code: String,
    onCode: (String) -> Unit,
) {
    val named = name.trim().isNotBlank()
    // Eight, because that is the length the server issues. Anything else is a
    // half-typed code and offering to join on it only produces a refusal.
    val complete = code.length == 8

    Card {
        Field(
            label = "Your name",
            hint = "What the room calls you",
            value = name,
            onValue = onName,
        )
        Field(
            label = "Room code",
            hint = "Leave empty to start your own",
            value = code,
            onValue = { onCode(it.uppercase().filter(Char::isLetterOrDigit).take(8)) },
            wide = true,
        )

        if (Together.knockingAtDoor) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Blaze.Amber.copy(alpha = 0.14f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Waiting for the host to let you in…",
                    color = Blaze.Amber, fontSize = 12.5.sp,
                )
            }
        }

        when {
            !named -> Text(
                "Pick a name first — it is what everybody else sees.",
                color = Blz.dim, fontSize = 11.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            complete -> Wide("Join room", filled = true, Modifier.fillMaxWidth()) {
                Together.join(code)
            }
            code.isEmpty() -> Wide("Create room", filled = true, Modifier.fillMaxWidth()) {
                Together.host()
            }
            else -> Text(
                "Room codes are eight characters — ${code.length} so far.",
                color = Blz.dim, fontSize = 11.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Who's here, and what they're waiting on. */
@Composable
private fun Room() {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Text(
                if (Together.hosting) "YOU'RE HOSTING" else "YOU'RE LISTENING IN",
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
            Wide("Leave room", filled = false, Modifier.fillMaxWidth()) { Together.leave() }
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

/**
 * The way to the rest of it.
 *
 * Server, name and what gets approved without asking all live in Settings, and
 * this is the page you are on when you want them — so the door is here rather
 * than only in the rail, which is exactly the arrangement the phone uses.
 */
@Composable
private fun SettingsCard() {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Blz.surface)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { Navigator.openSettings(SettingsPage.Together) }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.Tune, null, Modifier.size(22.dp), tint = Blaze.Amber)
        Column(Modifier.weight(1f)) {
            Text("Blaze Together settings", color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Server, your name, and what gets let in automatically", color = Blz.dim, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = Blz.dim)
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

/**
 * A labelled box you type in.
 *
 * The label sits above rather than inside, so it is still readable once there
 * is text in the field — a placeholder that vanishes the moment you use it is
 * a label you have to remember.
 */
@Composable
private fun Field(
    label: String,
    hint: String,
    value: String,
    onValue: (String) -> Unit,
    wide: Boolean = false,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Blz.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Blz.surfaceHigh)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    hint, color = Blz.dim, fontSize = 14.sp,
                    letterSpacing = if (wide) 2.sp else 0.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(
                    color = Blz.ink, fontSize = 14.sp,
                    letterSpacing = if (wide) 2.sp else 0.sp,
                ),
                cursorBrush = SolidColor(Blaze.Amber),
                modifier = Modifier
                    .fillMaxWidth()
                    // The letter shortcuts stand down while this has focus, or
                    // typing a room code would pause the music halfway through.
                    .onFocusChanged { Typing.active = it.isFocused },
            )
        }
    }
}

/** A button that takes the width it is given. */
@Composable
private fun Wide(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surfaceHigh),
            )
            .then(
                if (filled) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) Blaze.OnAmber else Blz.ink,
            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
        )
    }
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
