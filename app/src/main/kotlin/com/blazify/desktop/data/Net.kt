package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether the catalogue is reachable.
 *
 * Not "is there a network" — a machine can be on a wifi that goes nowhere, and
 * a captive portal answers everything. What matters is whether the one host
 * this app needs will accept a connection, so that is what gets asked.
 *
 * Assumed reachable until proved otherwise. Starting offline and discovering
 * otherwise a second later means every screen opens in its fallback shape and
 * then rearranges itself, which looks worse than being briefly wrong.
 */
object Net {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var online by mutableStateOf(true)
        private set

    /** True once the first real check has happened, so screens can wait for it. */
    var known by mutableStateOf(false)
        private set

    init {
        scope.launch {
            while (isActive) {
                val reachable = reachable()
                if (reachable != online) online = reachable
                known = true
                // Often enough that unplugging is noticed within a song, rarely
                // enough that it costs nothing. Quicker while offline, since
                // that is the state somebody is waiting to leave.
                delay(if (reachable) 30_000 else 6_000)
            }
        }
    }

    /** Ask again now — for a button, or after something failed. */
    fun recheck() {
        scope.launch {
            val reachable = reachable()
            if (reachable != online) online = reachable
            known = true
        }
    }

    private fun reachable(): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("music.youtube.com", 443), 2500)
            true
        }
    }.getOrDefault(false)
}
