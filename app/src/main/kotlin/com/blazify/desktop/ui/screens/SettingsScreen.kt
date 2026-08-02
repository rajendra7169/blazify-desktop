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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.blazify.desktop.data.Playlists
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.Typing
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Store
import com.blazify.desktop.ui.Artwork
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
/**
 * Which part of the settings you're looking at.
 *
 * The list is long enough that one scroll buries things — someone hunting for
 * the equaliser shouldn't have to pass the whole of Look and Feel to reach it.
 * Named after what you'd be trying to change rather than after where the code
 * happens to live.
 */
private enum class SettingsPage(val label: String, val icon: ImageVector) {
    Account("Account", Icons.Rounded.Person),
    LookAndFeel("Look and feel", Icons.Rounded.Palette),
    PlayerAudio("Player and audio", Icons.Rounded.GraphicEq),
    Lyrics("Lyrics", Icons.Rounded.Lyrics),
    Content("Content", Icons.Rounded.LibraryMusic),
    Storage("Storage", Icons.Rounded.Storage),
    Privacy("Privacy", Icons.Rounded.Shield),
    Keyboard("Keyboard", Icons.Rounded.Keyboard),
    About("About", Icons.Rounded.Info),
}

/**
 * The settings, with their own rail.
 *
 * Every row here changes behaviour you can see. A settings screen padded with
 * switches that toggle nothing is worse than a short one — it teaches people
 * their choices don't matter.
 */
@Composable
fun SettingsScreen() {
    var page by remember { mutableStateOf(SettingsPage.Account) }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(228.dp)
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Settings", color = Blz.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp, bottom = 14.dp),
            )
            SettingsPage.entries.forEach { entry ->
                PageRow(entry, entry == page) { page = entry }
            }
        }

        Box(Modifier.fillMaxHeight().width(1.dp).background(Blz.line))

        LazyColumn(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Text(page.label, color = Blz.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            when (page) {
                SettingsPage.Account -> item { AccountSection() }

                SettingsPage.LookAndFeel -> {
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
                    item { LookAndFeelSection { title, content -> Section(title) { content() } } }
                }

                SettingsPage.PlayerAudio -> {
                    item { EqualiserSection { title, content -> Section(title) { content() } } }
                }

                SettingsPage.Lyrics -> item {
                    LyricsSettingsSection { title, content -> Section(title) { content() } }
                }

                SettingsPage.Content -> item {
                    Section("Where your music comes from") {
                        Line("Songs on this computer", "${LocalMusic.tracks.size}")
                        Line("Music folders watched", "${LocalMusic.folders.size}")
                        Line("Playlists made here", "${Playlists.all.size}")
                        Text(
                            "Folders are added from On this computer, in the rail.",
                            color = Blz.dim, fontSize = 11.5.sp,
                        )
                    }
                }

                SettingsPage.Storage -> item {
                    Section("Storage") {
                        Line("Where everything is kept", Store.folder.absolutePath)
                        Line("Downloaded songs", "${Downloads.items.size}  ·  ${size(Downloads.bytes)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (Downloads.items.isNotEmpty()) {
                                Button("Remove downloads", Downloads::removeAll)
                            }
                        }
                    }
                }

                SettingsPage.Privacy -> item {
                    Section("What's remembered") {
                        Line("Liked songs", "${Library.liked.size}")
                        Line("History", "${Library.history.size}")
                        Line("Saved albums and playlists", "${Library.saved.size}")
                        Text(
                            "All of it stays on this machine. Nothing is sent anywhere but the catalogue, " +
                                "and only to fetch what you asked for.",
                            color = Blz.dim, fontSize = 11.5.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (Library.history.isNotEmpty()) {
                                Button("Clear history", Library::clearHistory)
                            }
                        }
                    }
                }

                SettingsPage.Keyboard -> item {
                    Section("Keyboard") {
                        Line("Play or pause", "Space  ·  K")
                        Line("Back and forward five seconds", "← →  ·  J  L")
                        Line("Previous and next track", "P  ·  N")
                        Line("Volume", "↑ ↓")
                        Line("Mute", "M")
                        Line("Media keys", "Play, pause, next, previous")
                        Text(
                            "The letter shortcuts stand down while you're typing. The media keys never do.",
                            color = Blz.dim, fontSize = 11.5.sp,
                        )
                    }
                }

                SettingsPage.About -> item {
                    Section("About") {
                        Line("Blazify", "Version 1.0.0")
                        Line("A music player", "for Linux and Windows")
                    }
                }
            }
        }
    }
}

@Composable
private fun PageRow(page: SettingsPage, selected: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    val tint = when {
        selected -> Blaze.Amber
        hovered.value -> Blz.ink
        else -> Blz.muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blaze.Amber.copy(alpha = 0.13f) else Color.Transparent)
            .then(if (selected) Modifier else Modifier.hoverBackground(Blz.hover, hovered, source))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(page.icon, page.label, Modifier.size(18.dp), tint = tint)
        Text(
            page.label, color = tint, fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AccountSection() {
    var pasting by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    Section("Account") {
        if (Account.signedIn) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Account.picture?.let {
                    Artwork(it, size = 40.dp, corner = 20.dp, modifier = Modifier.clip(CircleShape))
                    Spacer(Modifier.size(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        Account.name ?: if (Account.checking) "Checking…" else "Signed in",
                        color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Account.email?.let { Text(it, color = Blz.muted, fontSize = 12.sp) }
                }
                Button("Sign out", Account::signOut)
            }
            Text(
                "Your playlists, your history and a feed built out of what you actually listen to.",
                color = Blz.dim, fontSize = 11.5.sp,
            )
        } else if (!pasting) {
            Line("Signed in", "No — the feed is what's popular, not what's yours")
            Button("Sign in") { pasting = true }
        } else {
            Text(
                "Sign in at music.youtube.com in your browser, open its developer tools, " +
                    "find any request to the site, and copy the whole Cookie header. Paste it here.",
                color = Blz.muted, fontSize = 12.5.sp, lineHeight = 18.sp,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Blz.surfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (pasted.isEmpty()) {
                    Text("Cookie: VISITOR_INFO1_LIVE=…; SAPISID=…", color = Blz.dim, fontSize = 12.sp)
                }
                BasicTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    textStyle = TextStyle(color = Blz.ink, fontSize = 12.sp),
                    cursorBrush = SolidColor(Blaze.Amber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { Typing.active = it.isFocused },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("Sign in") { Account.signIn(pasted); pasted = "" }
                Button("Cancel") { pasting = false; pasted = "" }
            }
            Text(
                "Kept on this machine only, and sent nowhere but the catalogue.",
                color = Blz.dim, fontSize = 11.5.sp,
            )
        }

        Account.problem?.let { Text(it, color = Blaze.Ember, fontSize = 12.sp) }
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
