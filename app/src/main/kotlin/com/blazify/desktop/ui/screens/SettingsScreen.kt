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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Store
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.ThemeMode
import com.blazify.desktop.ui.ThemeState
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The settings, kept to things that actually do something.
 *
 * Every row here changes behaviour you can see. A settings screen padded with
 * switches that toggle nothing is worse than a short one — it teaches people
 * their choices don't matter.
 */
@Composable
fun SettingsScreen() {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
            Text("Settings", color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Section("Appearance") {
                Choice(
                    label = "Theme",
                    note = "System follows the dark-first default until the desktop can be asked",
                    options = ThemeMode.entries.map { it.name },
                    selected = ThemeState.mode.name,
                    onSelect = { ThemeState.set(ThemeMode.valueOf(it)) },
                )
            }
        }

        item {
            Section("Storage") {
                Line("Where everything is kept", Store.folder.absolutePath)
                Line("Downloaded songs", "${Downloads.items.size}  ·  ${size(Downloads.bytes)}")
                Line("Music folders watched", "${LocalMusic.folders.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (Downloads.items.isNotEmpty()) {
                        Button("Remove downloads", Downloads::removeAll)
                    }
                    if (Library.history.isNotEmpty()) {
                        Button("Clear history", Library::clearHistory)
                    }
                }
            }
        }

        item {
            Section("Library") {
                Line("Liked songs", "${Library.liked.size}")
                Line("Saved albums and playlists", "${Library.saved.size}")
                Line("Songs on this computer", "${LocalMusic.tracks.size}")
            }
        }

        item {
            Section("Keyboard") {
                Line("Play or pause", "Space  ·  K")
                Line("Back and forward five seconds", "← →  ·  J  L")
                Line("Previous and next track", "P  ·  N")
                Line("Volume", "↑ ↓")
                Line("Mute", "M")
                Line("Media keys", "Play, pause, next, previous")
            }
        }

        item {
            Section("About") {
                Line("Blazify", "Version 1.0.0")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title.uppercase(), color = Blz.dim, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Blz.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A fact and its value, which is most of what this screen is. */
@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Blz.ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(
            value, color = Blz.muted, fontSize = 12.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 380.dp),
        )
    }
}

@Composable
private fun Choice(
    label: String,
    note: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = Blz.ink, fontSize = 13.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val on = option == selected
                val (source, hovered) = rememberHovered()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                            else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
                        )
                        .then(if (on) Modifier else Modifier.hoverBackground(Blz.hover, hovered, source))
                        .clickable { onSelect(option) }
                        .padding(horizontal = 15.dp, vertical = 7.dp),
                ) {
                    Text(
                        option,
                        color = if (on) Blaze.OnAmber else Blz.muted,
                        fontSize = 12.5.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Text(note, color = Blz.dim, fontSize = 11.5.sp)
    }
}

@Composable
private fun Button(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Blz.surfaceHigh)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(label, color = Blz.muted, fontSize = 12.5.sp)
    }
}

private fun size(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes > 0 -> "%.0f KB".format(bytes / 1000.0)
    else -> "nothing yet"
}
