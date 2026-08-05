package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Offline
import com.blazify.desktop.data.Panel
import com.blazify.desktop.data.Paxsenix
import com.blazify.desktop.ui.AppShell
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.BlazifyTheme
import com.blazify.desktop.ui.blazifyMark
import com.blazify.desktop.ui.MiniPlayer
import com.blazify.desktop.ui.ThemeState
import com.blazify.desktop.ui.WindowMode

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

fun main() {
    // Before the window, so the very first fetch already knows whose it is.
    Account.restore()
    // Apple's key takes a while to read and everything else waits on it. Read
    // now, while the window is still being built and nobody is looking at an
    // empty lyric sheet.
    Paxsenix.warmKey()
    // Covers and lyrics for anything kept before those were saved alongside.
    Offline.catchUp()
    // Answer the desktop when it asks what is playing, so the media key on a
    // keyboard reaches this rather than nothing.
    Panel.start()
    run()
}

private fun run() = application {
    val state = rememberWindowState(
        // Opened at the size of the screen. Every page here is rails of
        // artwork and lists of songs, and both are the sort of thing where a
        // window covering two thirds of a monitor is two thirds of a page —
        // the first thing anybody did was drag it bigger.
        placement = WindowPlacement.Maximized,
        // What it goes back to when it stops being maximised: wide enough for
        // the rail, the content and the queue panel side by side, which is the
        // layout the whole app is built around.
        size = DpSize(1180.dp, 760.dp),
    )

    // Closing the window ends the application.
    //
    // It used to hide instead, and stay alive behind an icon in the bar. That
    // is a reasonable thing for a music player to do and it was the wrong
    // thing here: closing the window looks like quitting, so people then
    // opened it again from the launcher — which started a second copy, with
    // the first still playing somewhere they could not see. A player you have
    // to hunt for in order to stop is worse than one that stops when you
    // close it.

    // The small window replaces the big one rather than joining it. Two windows
    // of the same player, both live, is a confusion — which one is in charge?
    val mini = WindowMode.mini

    val miniState = rememberWindowState(
        size = DpSize(430.dp, 118.dp),
        position = WindowPosition.Aligned(Alignment.BottomEnd),
    )

    if (mini) {
        Window(
            onCloseRequest = WindowMode::full,
            icon = blazifyMark,
            state = miniState,
            title = "Blazify",
            resizable = false,
            alwaysOnTop = true,
            onKeyEvent = { Shortcuts.handle(it, typing = false) },
        ) {
            BlazifyTheme(dark = ThemeState.isDark()) {
                MiniPlayer(onExpand = WindowMode::full)
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        visible = !mini,
        title = "Blazify",
        // The dock, the switcher and the title bar all ask the window itself
        // what it looks like, and get nothing unless it is told.
        icon = blazifyMark,
        // Claimed at the window rather than on any one control, so the keys
        // work wherever you happen to be looking.
        onKeyEvent = { Shortcuts.handle(it, typing = Typing.active) },
    ) {
        window.minimumSize = java.awt.Dimension(940, 600)

        BlazifyTheme(dark = ThemeState.isDark()) { AppShell() }
    }
}
