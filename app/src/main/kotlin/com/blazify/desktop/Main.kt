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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blazify.desktop.ui.AppShell
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.BlazifyTheme
import com.blazify.desktop.ui.ThemeState

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

fun main() = application {
    val state = rememberWindowState(
        // Wide enough for the rail, the content and the queue panel side by side,
        // which is the layout the whole app is built around.
        size = DpSize(1180.dp, 760.dp),
    )

    // Closing the window puts it away rather than ending the music. Quitting is
    // its own choice, on the tray menu — a player that stops the moment its
    // window is dismissed makes people afraid to tidy their desktop.
    var showing by remember { mutableStateOf(true) }

    Tray(
        icon = Tinted(rememberVectorPainter(Icons.Rounded.LocalFireDepartment), Blaze.Amber),
        tooltip = PlayerState.current?.let { "${it.title} — ${it.artist}" } ?: "Blazify",
        onAction = { showing = true },
        menu = {
            Item(if (PlayerState.playing) "Pause" else "Play", onClick = PlayerState::toggle)
            Item("Next", onClick = PlayerState::next)
            Item("Previous", onClick = PlayerState::previous)
            Separator()
            Item(if (showing) "Hide window" else "Show window", onClick = { showing = !showing })
            Item("Quit Blazify", onClick = ::exitApplication)
        },
    )

    Window(
        onCloseRequest = { showing = false },
        state = state,
        visible = showing,
        title = "Blazify",
        // Claimed at the window rather than on any one control, so the keys
        // work wherever you happen to be looking.
        onKeyEvent = { Shortcuts.handle(it, typing = Typing.active) },
    ) {
        window.minimumSize = java.awt.Dimension(940, 600)

        BlazifyTheme(dark = ThemeState.isDark()) { AppShell() }
    }
}
