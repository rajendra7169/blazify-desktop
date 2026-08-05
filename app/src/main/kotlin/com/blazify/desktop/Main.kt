package com.blazify.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
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

/**
 * The same drawing in a different colour.
 *
 * A tray sits on whatever the desktop's panel happens to look like, and the
 * icon arrives in the ink it was drawn with — black, which disappears on half
 * of them. Colouring it amber makes it ours and makes it visible on both.
 */
private class Tinted(private val inner: Painter, private val colour: androidx.compose.ui.graphics.Color) : Painter() {
    override val intrinsicSize: Size get() = inner.intrinsicSize
    override fun DrawScope.onDraw() {
        with(inner) { draw(size, colorFilter = ColorFilter.tint(colour)) }
    }
}

fun main() {
    // Before any window exists, because it cannot be changed after one does.
    nameTheWindow()
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

/**
 * Tell the desktop what this window is called.
 *
 * A window carries a class name, and the desktop matches that against the
 * installed applications to decide which icon to draw in the bar and which
 * entry to highlight in the dock. Left alone it is whatever the toolkit
 * guessed from the class that started the process, which matches nothing —
 * so the package installs a Blazify icon and the running window shows a
 * generic one beside it, which looks like two different programs.
 *
 * There is no supported way to set it. This is the one that works, and it is
 * wrapped in a shrug because a desktop that will not have it is a desktop with
 * a plain icon rather than a broken application.
 */
private fun nameTheWindow() {
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, "Blazify")
    }
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

    // Closing the window puts it away rather than ending the music. Quitting is
    // its own choice, on the tray menu — a player that stops the moment its
    // window is dismissed makes people afraid to tidy their desktop.
    var showing by remember { mutableStateOf(true) }


    Tray(
        // The application's own mark, not a flame from an icon set tinted to
        // look like it. Everywhere else on this desktop shows what it is up
        // there — the mark is how somebody finds this among nine other
        // indicators, and a generic shape says only "something is running".
        //
        // The drawn flame stays as the fallback, for a build where the image
        // cannot be read: an empty square in a system tray is worse than an
        // approximate one.
        icon = blazifyMark
            ?: Tinted(rememberVectorPainter(Icons.Rounded.LocalFireDepartment), Blaze.Amber),
        tooltip = PlayerState.current?.let { "${it.title} — ${it.artist}" } ?: "Blazify",
        onAction = { showing = true },
        menu = {
            Item(if (PlayerState.playing) "Pause" else "Play", onClick = PlayerState::toggle)
            Item("Next", onClick = PlayerState::next)
            Item("Previous", onClick = PlayerState::previous)
            Separator()
            Item(if (WindowMode.mini) "Full window" else "Mini player", onClick = WindowMode::toggleMini)
            Item(if (showing) "Hide window" else "Show window", onClick = { showing = !showing })
            Item("Quit Blazify", onClick = ::exitApplication)
        },
    )

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
                MiniPlayer(onExpand = { WindowMode.full(); showing = true })
            }
        }
    }

    Window(
        onCloseRequest = { showing = false },
        state = state,
        visible = showing && !mini,
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
