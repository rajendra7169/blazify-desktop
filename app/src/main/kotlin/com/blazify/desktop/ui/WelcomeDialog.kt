package com.blazify.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Welcome

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** One page of the welcome: a picture, a heading and a few lines. */
private data class WelcomePage(
    val icon: ImageVector?,
    val title: String,
    val body: String,
)

private val pages = listOf(
    WelcomePage(
        icon = null,
        title = "Welcome to Blazify",
        body = "A music player for this computer. Search for any song, album or artist, " +
            "play what's already on your disk, and keep favourites for when the " +
            "connection isn't there.",
    ),
    WelcomePage(
        icon = Icons.Rounded.Person,
        title = "Signing in is optional",
        body = "Everything plays without an account. Sign in from Settings, under Account, " +
            "if you want your own playlists and a home page built from what you listen to.",
    ),
    WelcomePage(
        icon = Icons.Rounded.Keyboard,
        title = "Settings and shortcuts",
        body = "Settings sit at the bottom of the rail on the left. The keyboard does the rest:",
    ),
    WelcomePage(
        icon = Icons.Rounded.PlayArrow,
        title = "That's all",
        body = "Your plays add up under Top songs as you go. This won't be shown again — " +
            "it can be found under Settings, About, if you ever want it.",
    ),
)

/**
 * The first-run welcome.
 *
 * Four short pages and a way out on every one of them. Somebody who opened a
 * music player wants to play music, so nothing here is required reading and
 * Skip is as easy to reach as Next.
 */
@Composable
fun WelcomeDialog() {
    if (!Welcome.showing) return
    var at by remember { mutableStateOf(0) }
    val last = at == pages.lastIndex

    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.Scrim)
            // Swallows clicks rather than dismissing: a stray click beside it
            // on the very first launch shouldn't throw the whole thing away.
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AnimatedContent(
                targetState = at,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            ) { index ->
                Page(pages[index], index)
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(width = if (index == at) 18.dp else 7.dp, height = 7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (index == at) Blaze.Amber else Blz.surfaceHigh),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!last) TextButton("Skip", Blz.muted, Welcome::done)
                if (at > 0) TextButton("Back", Blz.muted) { at -= 1 }
                FilledButton(if (last) "Start listening" else "Next") {
                    if (last) Welcome.done() else at += 1
                }
            }
        }
    }
}

@Composable
private fun Page(page: WelcomePage, index: Int) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 250.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (page.icon == null) {
            BlazifyLogo(size = 64.dp)
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, null, Modifier.size(28.dp), tint = Blaze.OnAmber)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(page.title, color = Blz.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(page.body, color = Blz.dim, fontSize = 13.5.sp, lineHeight = 20.sp)

        // The shortcuts themselves, rather than a sentence promising they exist.
        if (index == 2) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Shortcut("Play or pause", "Space")
                Shortcut("Back or forward five seconds", "←  →")
                Shortcut("Previous or next song", "P  ·  N")
                Shortcut("Volume and mute", "↑  ↓  ·  M")
            }
        }
    }
}

@Composable
private fun Shortcut(label: String, keys: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Blz.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            keys,
            color = Blz.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Blz.surfaceHigh)
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TextButton(label: String, colour: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Text(
        label,
        color = colour,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun FilledButton(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
            .hoverGlow(hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(label, color = Blaze.OnAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
