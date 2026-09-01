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
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.People
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
import com.blazify.desktop.data.SignInWindow
import com.blazify.desktop.data.BrowserSession
import com.blazify.desktop.data.Backup
import com.blazify.desktop.data.Cache
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Levelling
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Offline
import com.blazify.desktop.data.Notify
import com.blazify.desktop.data.Panel
import com.blazify.desktop.data.Store
import com.blazify.desktop.data.Updates
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Navigator
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.ThemeMode
import com.blazify.desktop.ui.ThemeState
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
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
enum class SettingsPage(val label: String, val icon: ImageVector) {
    Account("Account", Icons.Rounded.Person),
    LookAndFeel("Look and feel", Icons.Rounded.Palette),
    Together("Blaze Together", Icons.Rounded.People),
    Connections("Connections", Icons.Rounded.Link),
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
    // Opened at whichever page asked for it. A link from a feature's own screen
    // that lands on Account is a link you have to finish following by hand.
    var page by remember { mutableStateOf(Navigator.settingsPage) }

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
                modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 14.dp),
            )
            SettingsPage.entries.forEach { entry ->
                PageRow(entry, entry == page) { page = entry }
            }

            // The way out sits where the way in was.
            //
            // Settings is opened from the bottom of the rail and was left from
            // the top of it, so the two halves of one journey were at opposite
            // ends of the screen — you pressed a thing down here and then went
            // hunting up there to undo it. Now the door is in the same place
            // both times.
            Spacer(Modifier.weight(1f))
            BackRow()
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

                SettingsPage.Connections -> item {
                    ConnectionsSettingsSection { title, reset, content ->
                        Section(title, reset) { content() }
                    }
                }

                SettingsPage.Together -> item {
                    TogetherSettingsSection { title, reset, content ->
                        Section(title, reset) { content() }
                    }
                }

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
                    item { LookAndFeelSection { title, reset, content -> Section(title, reset) { content() } } }
                }

                SettingsPage.PlayerAudio -> {
                    item {
                        PlaybackSettingsSection { title, reset, content ->
                            Section(title, reset) { content() }
                        }
                    }
                    item { EqualiserSection { title, reset, content -> Section(title, reset) { content() } } }

                    item {
                        Section("Levelling") {
                            Text(
                                "A record mastered in 1975 and one mastered last year are ten " +
                                    "decibels apart, and a queue that mixes them is one you ride " +
                                    "with a hand on the volume. This evens out what is heard " +
                                    "rather than trusting what a file claims, because almost " +
                                    "nothing played here carries a loudness figure at all.",
                                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                            SettingSwitch("Even out the volume", Levelling.on) { Levelling.choose(it) }
                            Text(
                                if (Levelling.on) {
                                    "It is a compressor, so it takes the top off the loud moments " +
                                        "— which is exactly what a qawwali building for eight " +
                                        "minutes does not want. Takes effect next time Blazify " +
                                        "starts: this belongs to the machinery rather than to a " +
                                        "track, and cannot be changed under one that is playing."
                                } else {
                                    "Takes effect next time Blazify starts."
                                },
                                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                            )
                        }
                    }

                    item {
                        Section("The desktop") {
                            Text(
                                if (Notify.available) {
                                    "The window is usually behind something else, so the moment " +
                                        "worth interrupting for is the moment the song changes."
                                } else {
                                    "This desktop has no notification service to tell, so there " +
                                        "is nothing to turn on."
                                },
                                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                            if (Notify.available) {
                                SettingSwitch("Say what came on", Notify.on) { Notify.choose(it) }
                            }

                            SettingSwitch(
                                "Answer the desktop's media controls",
                                Panel.on,
                            ) { Panel.choose(it) }
                            Text(
                                "The controls in the panel and the calendar, and the play, pause " +
                                    "and skip keys on a keyboard — all one thing. Turning it off " +
                                    "takes the keys with it.",
                                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                            )
                        }
                    }
                    item {
                        StreamSettingsSection { title, reset, content ->
                            Section(title, reset) { content() }
                        }
                    }
                }

                SettingsPage.Lyrics -> item {
                    LyricsSettingsSection { title, reset, content -> Section(title, reset) { content() } }
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

                SettingsPage.Storage -> {
                    item {
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

                    item {
                        Section("Kept as you listen") {
                            Text(
                                "Songs are kept on this machine as you play them, with their " +
                                    "covers and words, so they still play when the connection " +
                                    "doesn't. The oldest go first when there is no room left — " +
                                    "it is a good chance rather than a promise, which is what a " +
                                    "download is for.",
                                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                            SettingSwitch("Keep songs as I play them", Cache.on) {
                                Cache.choose(it)
                            }
                            // Broken out the way the downloads are, and for
                            // the same reason: somebody clearing space needs to
                            // know which of the two is actually the problem.
                            Line("Songs kept", "${Cache.items.size}  ·  ${size(Cache.bytes)}")
                            Line(
                                "Covers and words",
                                "${Offline.artCount} covers, ${Offline.wordCount} sets  ·  " +
                                    size(Offline.artBytes + Offline.wordBytes),
                            )
                            Choice(
                                label = "How much room it may use",
                                note = "The oldest are thrown away to stay under it",
                                options = listOf("512 MB", "1 GB", "2 GB", "5 GB", "10 GB"),
                                selected = when (Cache.limitMegabytes) {
                                    512 -> "512 MB"
                                    1024 -> "1 GB"
                                    5120 -> "5 GB"
                                    10240 -> "10 GB"
                                    else -> "2 GB"
                                },
                                onSelect = {
                                    Cache.chooseLimit(
                                        when (it) {
                                            "512 MB" -> 512
                                            "1 GB" -> 1024
                                            "5 GB" -> 5120
                                            "10 GB" -> 10240
                                            else -> 2048
                                        },
                                    )
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (Cache.items.isNotEmpty()) {
                                    Button("Clear kept songs", Cache::forgetAll)
                                }
                                if (Offline.artBytes + Offline.wordBytes > 0) {
                                    // Safe to throw away in a way the audio is
                                    // not: these come back by themselves the
                                    // next time each song plays.
                                    Button("Clear covers and words", Offline::forgetExtras)
                                }
                            }
                        }
                    }

                    item {
                        Section("Backup") {
                            Text(
                                "Everything you've liked, played, saved and made, plus how " +
                                    "you've set the app up — one ordinary zip of ordinary " +
                                    "JSON, for a new machine or a reinstall.",
                                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                            Line("This backup would hold", Backup.summary())

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(if (Backup.busy) "Working…" else "Back up") {
                                    if (!Backup.busy) {
                                        chooseSaveFile(Backup.suggestedName())?.let(Backup::writeTo)
                                    }
                                }
                                Button("Restore") {
                                    if (!Backup.busy) chooseOpenFile()?.let(Backup::readFrom)
                                }
                            }

                            Backup.outcome?.let {
                                Text(it, color = Blaze.Amber, fontSize = 12.sp, lineHeight = 18.sp)
                            }

                            Text(
                                "Your sign-in is left out on purpose. A copy of it in a file " +
                                    "that gets emailed around is a copy of the account, and " +
                                    "signing in again takes ten seconds.",
                                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                            )
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

                SettingsPage.About -> {
                    item {
                        AboutSection { title, reset, content -> Section(title, reset) { content() } }
                    }

                    item {
                        Section("About") {
                            Line("Blazify", "Version ${Updates.RUNNING}")
                            Line("A music player", "for Linux and Windows")
                        }
                    }

                    item {
                        Section("Updates") {
                            SettingSwitch(
                                "Check when Blazify starts",
                                Updates.checkOnStart,
                                Updates::chooseCheckOnStart,
                            )
                            Text(
                                "Off by default. A program that phones home every launch " +
                                    "should have been asked first, and \"is there a new " +
                                    "version\" is almost never urgent.",
                                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(if (Updates.checking) "Checking…" else "Check now") {
                                    Updates.check()
                                }
                                if (Updates.newer) {
                                    Button("Open the release page", Updates::openReleases)
                                }
                            }

                            Updates.outcome?.let {
                                Text(
                                    it,
                                    color = if (Updates.newer) Blaze.Amber else Blz.muted,
                                    fontSize = 12.5.sp,
                                )
                            }

                            Updates.notes?.takeIf { Updates.newer }?.let { notes ->
                                Text(
                                    notes.lineSequence().take(12).joinToString("\n"),
                                    color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                                )
                            }

                            Text(
                                "This only looks. Nothing is downloaded or replaced — on " +
                                    "Linux that would be arguing with whatever package " +
                                    "manager installed it.",
                                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A switch, for the pages that build their rows here rather than in a section
 * file of their own.
 */
@Composable
private fun SettingSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onChange(!on) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Blz.ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(38.dp)
                .height(21.dp)
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
                    .size(15.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (on) Blaze.OnAmber else Blz.muted),
            )
        }
    }
}

@Composable
private fun BackRow() {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = Navigator::closeSettings)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(19.dp), tint = Blz.muted)
        Text("Back to ${Navigator.destination.label}", color = Blz.muted, fontSize = 14.5.sp)
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

/**
 * Signing in.
 *
 * The whole exchange happens on Google's own page in your own browser: this
 * screen only shows the code to type there and waits. Nothing is typed into
 * the application, and no password ever passes through it.
 */
/**
 * Signing in.
 *
 * One button. You sign in to YouTube Music in whatever browser you already
 * use, and this brings that session across — the machine works out which
 * browser for itself rather than asking a question it can answer. Pasting the
 * session by hand is kept for when that fails, and only then.
 */
@Composable
private fun AccountSection() {
    var pasting by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    Section("Account") {
        if (Account.signedIn) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Account.picture?.let {
                    Artwork(it, size = 44.dp, corner = 22.dp, modifier = Modifier.clip(CircleShape))
                    Spacer(Modifier.size(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        Account.name ?: "Signed in",
                        color = Blz.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Account.email?.let { Text(it, color = Blz.muted, fontSize = 12.5.sp) }
                }
                Button("Sign out", Account::signOut)
            }
            Text(
                "Your playlists, your history and a feed built out of what you actually listen to.",
                color = Blz.dim, fontSize = 11.5.sp,
            )
            return@Section
        }

        // The window this app opens is the way in that holds still. Reading the
        // everyday browser is kept underneath it, because when it works it is
        // one press and no window at all — but it only works when that browser
        // has been quit, and the site moves the session on regardless.
        Text(
            Account.waitingForWindow?.let { browser ->
                when (Account.windowStage) {
                    SignInWindow.Stage.SignedIn ->
                        "Signed in — closing the $browser window."
                    SignInWindow.Stage.Collecting ->
                        "Asking $browser for the session. A moment, and no window this time."
                    else ->
                        "Sign in to YouTube Music in the $browser window that just opened. " +
                            "It closes itself once you're through."
                }
            } ?: "A window opens on Google's own sign-in page and closes itself once you're " +
                "signed in. Nothing is typed into Blazify and no password passes through it.",
            color = Blz.muted, fontSize = 13.sp, lineHeight = 18.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (Account.canOpenWindow) {
                GoogleButton(
                    when {
                        Account.windowStage == SignInWindow.Stage.SignedIn ||
                            Account.windowStage == SignInWindow.Stage.Collecting -> "Finishing…"
                        Account.waitingForWindow != null -> "Waiting for you to sign in…"
                        Account.checking -> "Signing in…"
                        else -> "Sign in"
                    },
                ) { Account.signInWithWindow() }
            }
            Button(
                if (Account.checking && Account.waitingForWindow == null) "Looking…"
                else "Use a browser I've already quit",
            ) { Account.signInFromBrowser() }
            Button("Open YouTube Music") { openInBrowser("https://music.youtube.com") }
        }

        Account.problem?.let { trouble ->
            Text(trouble, color = Blaze.Ember, fontSize = 12.sp, lineHeight = 17.sp)

            // Offered only once the easy way has actually failed, so nobody is
            // made to choose between two ways of doing the same thing.
            if (!pasting) {
                Button("Paste the session by hand instead") { pasting = true }
            }
        }

        if (pasting) {
            Text(
                "In the browser tab where you're signed in: press F12, open Network, reload, " +
                    "click any request to music.youtube.com, and copy the whole Cookie line " +
                    "from Request Headers.",
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
            Button("Use this session") { Account.signIn(pasted); pasted = "" }
        }

        Text(
            "Only the catalogue's own cookies are read, and only when you press the button. " +
                "They stay on this machine.",
            color = Blz.dim, fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun GoogleButton(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.AccountCircle, null, Modifier.size(18.dp), tint = Blaze.OnAmber)
        Text(
            label,
            color = Blaze.OnAmber, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Hand a page to whatever the desktop uses for the web.
 *
 * Wrapped because the desktop toolkit refuses on some window managers, and a
 * sign-in that dies on an exception rather than showing its code would be
 * unrecoverable — the code is on screen either way, so it can still be typed
 * in by hand.
 */
private fun openInBrowser(url: String) {
    runCatching {
        val desktop = java.awt.Desktop.getDesktop()
        if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
            desktop.browse(java.net.URI(url))
            return
        }
    }
    runCatching { ProcessBuilder("xdg-open", url).start() }
}

/**
 * One group of settings under its heading.
 *
 * The heading carries its own way back to the defaults when the group has one.
 * A single button at the bottom of the page can only mean "undo everything",
 * which is why nobody presses it — the useful question is "put this bit back",
 * and that has to be asked next to the bit.
 */
/**
 * The system's own save dialog.
 *
 * Swing's rather than something drawn here: this is a file being put somewhere
 * on a real disk, and people navigate their own machine faster in the picker
 * they already know than in anything an application invents.
 */
private fun chooseSaveFile(suggested: String): java.io.File? {
    val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
        dialogTitle = "Save a Blazify backup"
        selectedFile = java.io.File(suggested)
    }
    if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
    val picked = chooser.selectedFile ?: return null
    // Kept as a zip whatever it was named, so it opens by double-clicking.
    return if (picked.name.endsWith(".zip", true)) picked
    else java.io.File(picked.parentFile, picked.name + ".zip")
}

private fun chooseOpenFile(): java.io.File? {
    val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
        dialogTitle = "Open a Blazify backup"
    }
    if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.takeIf { it.isFile }
}

@Composable
private fun Section(
    title: String,
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 760.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(), color = Blz.dim, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
                modifier = Modifier.weight(1f),
            )
            onReset?.let { ResetHeadingButton(it) }
        }
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

/**
 * Put this group back.
 *
 * Faint until pointed at, because it is the one control on the page that undoes
 * work rather than doing any — it should be findable, not prominent.
 */
@Composable
private fun ResetHeadingButton(onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Restore, "Back to defaults", Modifier.size(15.dp),
            tint = if (hovered.value) Blz.ink else Blz.dim.copy(alpha = 0.45f),
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
                        .then(
                        if (on) Modifier.hoverGlow(hovered, source)
                        else Modifier.hoverBackground(Blz.hover, hovered, source),
                    )
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
