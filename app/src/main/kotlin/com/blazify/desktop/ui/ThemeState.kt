package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.prefs.Preferences

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Which appearance the window uses, remembered between runs.
 *
 * Stored in the platform preference store rather than a file of our own — it
 * lands in the registry on Windows and under ~/.java on Linux, and needs no
 * path handling on either.
 */
object ThemeState {
    private val store = Preferences.userRoot().node("com/blazify/desktop")
    private const val KEY = "themeMode"

    var mode: ThemeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(store.get(KEY, ThemeMode.System.name)) }
            .getOrDefault(ThemeMode.System),
    )
        private set

    fun set(value: ThemeMode) {
        mode = value
        runCatching { store.put(KEY, value.name) }
    }

    /** Step through the three, which is what a single toggle button does. */
    fun cycle() = set(
        when (mode) {
            ThemeMode.System -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.System
        },
    )

    /**
     * Whether to draw dark right now.
     *
     * There is no cross-platform way to ask the desktop, so System resolves to
     * dark — a music player is a dark-first thing, and anyone who disagrees is
     * one click from pinning it.
     */
    fun isDark(): Boolean = when (mode) {
        ThemeMode.System -> true
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
}
