package com.blazify.desktop.data

import com.blazify.desktop.PlayerState
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import javax.swing.SwingUtilities

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The media keys on a Windows keyboard, heard from behind other windows.
 *
 * Windows sends play, pause and skip to whichever window has focus, and a music
 * player is almost never the window with focus — it is the thing playing while
 * somebody works in something else. Left at that, the play key on a keyboard
 * only works on Blazify after clicking on Blazify, which is to say not at all.
 *
 * So the four keys are claimed for the whole desktop, the way a hotkey is: the
 * key goes to us wherever focus is. Claiming them is asked, not taken — if
 * another program already holds a key, Windows says no, and that key simply
 * keeps working the old way, inside our own window.
 *
 * Only on Windows. Everything here touches user32, which exists nowhere else;
 * the check comes first and nothing below it is reached on another system.
 */
object MediaKeys {

    private val windows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    // Virtual key codes, as Windows names them.
    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3

    /** One press is one press: a held key does not fire again and again. */
    private const val MOD_NOREPEAT = 0x4000

    private const val WM_HOTKEY = 0x0312
    private const val WM_QUIT = 0x0012

    /** Each key's id is its own key code — there is nothing to mix them up with. */
    private val keys = listOf(VK_MEDIA_PLAY_PAUSE, VK_MEDIA_NEXT_TRACK, VK_MEDIA_PREV_TRACK, VK_MEDIA_STOP)

    /** The one listening now, if any. */
    @Volatile private var listener: Listener? = null

    /**
     * Which keys Windows agreed to give us.
     *
     * The rest were taken by something else first and still work while our
     * window has focus — the window's own key handling never went away.
     */
    @Volatile var held: List<Int> = emptyList()
        private set

    fun start() {
        if (!windows) return
        if (listener != null) return
        // Its own thread, and it has to be. A hotkey is delivered to the
        // message queue of the thread that asked for it, so the asking and the
        // listening are one thread, which then waits for messages for as long
        // as the app runs.
        listener = Listener().also { it.start() }
    }

    fun stop() {
        val old = listener ?: return
        listener = null
        old.quit()
    }

    private class Listener : Thread("Blazify media keys") {

        init { isDaemon = true }

        /** This thread's id as Windows knows it, which is what has to be told to stop. */
        @Volatile private var id = 0

        @Volatile private var quitting = false

        fun quit() {
            quitting = true
            // The keys can only be given back by the thread that holds them, so
            // it is told to quit and does the giving back itself on the way out.
            // Told before it was listening, it sees `quitting` and never starts.
            val to = id
            if (to != 0) runCatching {
                User32.INSTANCE.PostThreadMessage(to, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
            }
        }

        override fun run() {
            runCatching {
                val user32 = User32.INSTANCE
                val message = WinUser.MSG()
                // Peeked once so this thread has a message queue before anyone
                // is told its id — a quit posted to a thread without one is lost.
                user32.PeekMessage(message, null, 0, 0, 0)
                id = Kernel32.INSTANCE.GetCurrentThreadId()
                val mine = keys.filter { key -> user32.RegisterHotKey(null, key, MOD_NOREPEAT, key) }
                held = mine
                if (mine.size < keys.size) {
                    System.err.println(
                        "Blazify: another program holds some media keys; those still work while this window has focus",
                    )
                }
                try {
                    // Zero is WM_QUIT, below zero is an error; either way, done.
                    while (!quitting && user32.GetMessage(message, null, 0, 0) > 0) {
                        if (message.message == WM_HOTKEY) press(message.wParam.toInt())
                    }
                } finally {
                    mine.forEach { user32.UnregisterHotKey(null, it) }
                    if (listener == null || listener === this) held = emptyList()
                }
            }.onFailure {
                // Never a reason to stop the music. The keys fall back to
                // working inside the window, as they always did.
                System.err.println(
                    "Blazify: global media keys are unavailable — ${it.javaClass.simpleName}: ${it.message}",
                )
            }
        }
    }

    /** Handed to the window's own thread, where every other button press happens. */
    private fun press(key: Int) = SwingUtilities.invokeLater {
        when (key) {
            VK_MEDIA_PLAY_PAUSE -> PlayerState.toggle()
            VK_MEDIA_NEXT_TRACK -> PlayerState.next()
            VK_MEDIA_PREV_TRACK -> PlayerState.previous()
            // Stop pauses, the same as it does in the window: there is no
            // further "stopped" here to be in.
            VK_MEDIA_STOP -> if (PlayerState.playing) PlayerState.toggle()
        }
    }
}
