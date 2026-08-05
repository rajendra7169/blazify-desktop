package com.blazify.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Recognise
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Listening to the room.
 *
 * One thing on screen at a time: it is listening, or it has an answer, or it
 * has a reason. A dialog that shows a spinner beside a result beside an
 * explanation is three states pretending to be one.
 */
@Composable
fun ListenSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<Recognise.Answer?>(null) }
    var listening by remember { mutableStateOf(false) }

    fun ask() {
        listening = true
        answer = null
        scope.launch {
            answer = Recognise.listen()
            listening = false
        }
    }

    // Starts on its own. Pressing a button to open this and another to begin
    // is asking twice for one thing.
    LaunchedEffect(Unit) { if (Recognise.ready && Recognise.canListen) ask() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Blz.page.copy(alpha = 0.86f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Blz.surface)
                .clickable(enabled = false) {}
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                !Recognise.canListen -> Explain(
                    "Nothing to listen with",
                    "This machine has no microphone that can be opened, so there is nothing " +
                        "to hear the room with.",
                    onDismiss,
                )

                !Recognise.ready -> Explain(
                    "Set up recognition first",
                    "Recognising a song from the air is done by a service, not by this " +
                        "application — it takes a free token from audd.io, which goes in " +
                        "Settings › Connections. The token is yours: one shipped inside the " +
                        "app would be one anybody could lift, and a spent quota would break " +
                        "this for everybody at once.",
                    onDismiss,
                )

                listening -> Listening()

                else -> when (val found = answer) {
                    is Recognise.Answer.Found -> Found(found.song, onDismiss)
                    is Recognise.Answer.Trouble -> Explain(
                        "That didn't work",
                        found.why,
                        onDismiss,
                        onAgain = ::ask,
                    )
                    else -> Explain(
                        "Nothing recognised",
                        "Ten seconds wasn't enough, or the song isn't one the service knows. " +
                            "Closer to the speaker and away from talking usually does it.",
                        onDismiss,
                        onAgain = ::ask,
                    )
                }
            }
        }
    }
}

/** A microphone with something happening around it, so the wait reads as work. */
@Composable
private fun Listening() {
    val beat = rememberInfiniteTransition(label = "listening")
    val pulse by beat.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(112.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(Blaze.Amber.copy(alpha = 0.12f)),
        )
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, null, Modifier.size(32.dp), tint = Blaze.OnAmber)
        }
    }
    Text("Listening…", color = Blz.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Hold this near the music for a few seconds.",
        color = Blz.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center,
    )
}

@Composable
private fun Found(song: Recognise.Heard, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var looking by remember { mutableStateOf(false) }

    Artwork(song.artwork, size = 132.dp, corner = 12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            song.title, color = Blz.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album)
                .joinToString("  ·  "),
            color = Blz.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }

    // The point of recognising it here rather than anywhere else: it can be
    // played straight away, in the thing that heard it.
    Pill(if (looking) "Finding it…" else "Play it", filled = true) {
        if (looking) return@Pill
        looking = true
        scope.launch {
            val found = Catalogue.search("${song.title} ${song.artist}".trim())
                .getOrDefault(emptyList())
                .firstOrNull()
            looking = false
            if (found != null) {
                PlayerState.play(listOf(found), 0, "Heard just now")
                onDismiss()
            }
        }
    }
    Pill("Close", filled = false, onClick = onDismiss)
}

@Composable
private fun Explain(
    title: String,
    detail: String,
    onDismiss: () -> Unit,
    onAgain: (() -> Unit)? = null,
) {
    Icon(Icons.Rounded.Mic, null, Modifier.size(40.dp), tint = Blz.dim)
    Text(title, color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    Text(
        detail, color = Blz.muted, fontSize = 12.5.sp, lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        onAgain?.let { Pill("Try again", filled = true, onClick = it) }
        Pill("Close", filled = false, onClick = onDismiss)
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
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(
            label,
            color = if (filled) Blaze.OnAmber else Blz.ink,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
