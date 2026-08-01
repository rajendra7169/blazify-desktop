package com.blazify.desktop

import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blazify.desktop.ui.AppShell
import com.blazify.desktop.ui.BlazifyTheme
import com.blazify.desktop.ui.ThemeState

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

fun main() = application {
    val state = rememberWindowState(
        // Wide enough for the rail, the content and the queue panel side by side,
        // which is the layout the whole app is built around.
        size = DpSize(1180.dp, 760.dp),
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "Blazify",
        // Claimed at the window rather than on any one control, so the keys
        // work wherever you happen to be looking.
        onKeyEvent = { Shortcuts.handle(it, typing = Typing.active) },
    ) {
        window.minimumSize = java.awt.Dimension(940, 600)

        BlazifyTheme(dark = ThemeState.isDark()) { AppShell() }
    }
}
