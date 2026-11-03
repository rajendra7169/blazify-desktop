package com.blazify.desktop.ui

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The title bar on Windows, in the same appearance as the window below it.
 *
 * Windows draws that bar itself and leaves it white unless the window says it
 * is dark, which put a bright strip across the top of an otherwise dark app.
 * Elsewhere the desktop's own theme decides, so this does nothing there.
 */
object TitleBar {
    private val onWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    @Suppress("FunctionName")
    private interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private val dwm: Dwm? by lazy {
        if (onWindows) runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() else null
    }

    // Windows 10 2004 and later, and Windows 11, read 20; the builds just
    // before them read 19 for the same thing.
    private const val DARK = 20
    private const val DARK_EARLIER = 19

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    fun follow(window: ComposeWindow, dark: Boolean) {
        val api = dwm ?: return
        // A bar that stays white is a cosmetic fault; a window that fails to
        // open over it would not be, so nothing here is allowed to throw.
        runCatching {
            val handle = window.windowHandle
            if (handle == 0L) return
            val hwnd = WinDef.HWND(Pointer(handle))
            val value = Memory(4).apply { setInt(0, if (dark) 1 else 0) }
            if (api.DwmSetWindowAttribute(hwnd, DARK, value, 4) != 0) {
                api.DwmSetWindowAttribute(hwnd, DARK_EARLIER, value, 4)
            }
            // Asks for the frame to be drawn again now, rather than the next
            // time the window happens to be moved or resized.
            User32.INSTANCE.SetWindowPos(
                hwnd, null, 0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
            )
        }
    }
}
