package com.blazify.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether the connection is counted by the byte.
 *
 * Fetching songs before they are wanted is free on a home connection and
 * costs real money on a phone's hotspot, so it is asked before anything is
 * fetched ahead. The desktop knows: on Linux the network manager keeps a
 * "metered" flag, guessed from the connection or set by hand; on Windows the
 * connection has a cost the system reports.
 *
 * When neither can be asked the answer is "not metered" — most computers are
 * on a cable or a home wifi, and the alternative is a feature that never works
 * on a machine without a network manager. Picking the data-saving quality is
 * taken as a plain statement that data matters, whatever the system says.
 */
object Metered {

    private var known: Boolean? = null
    private var askedAt = 0L

    /** True when fetching ahead is fine. Asked at most every five minutes. */
    suspend fun free(): Boolean {
        if (Streams.quality == Streams.Quality.Saver) return false
        val now = System.currentTimeMillis()
        known?.takeIf { now - askedAt < 5 * 60_000 }?.let { return !it }
        val metered = withContext(Dispatchers.IO) { ask() }
        known = metered
        askedAt = now
        return !metered
    }

    private fun ask(): Boolean = runCatching {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
        if (windows) {
            // Unrestricted is the only cost that means nothing is counted.
            val cost = run(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "[Windows.Networking.Connectivity.NetworkInformation,Windows.Networking.Connectivity," +
                    "ContentType=WindowsRuntime]::GetInternetConnectionProfile()" +
                    ".GetConnectionCost().NetworkCostType",
            )?.trim()
            cost != null && cost.isNotEmpty() && !cost.equals("Unrestricted", ignoreCase = true) &&
                !cost.equals("Unknown", ignoreCase = true)
        } else {
            // 1 is metered and 3 is guessed metered; 0, 2 and 4 are not.
            val answer = run(
                "busctl", "get-property", "org.freedesktop.NetworkManager",
                "/org/freedesktop/NetworkManager", "org.freedesktop.NetworkManager", "Metered",
            ) ?: run(
                "dbus-send", "--system", "--print-reply", "--dest=org.freedesktop.NetworkManager",
                "/org/freedesktop/NetworkManager", "org.freedesktop.DBus.Properties.Get",
                "string:org.freedesktop.NetworkManager", "string:Metered",
            )
            val value = answer?.trim()?.split(Regex("\\s+"))?.lastOrNull()?.toIntOrNull()
            value == 1 || value == 3
        }
    }.getOrDefault(false)

    /** A command's output, or null if it couldn't run or said nothing useful. */
    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        process.inputStream.bufferedReader().readText()
    }.getOrNull()
}
