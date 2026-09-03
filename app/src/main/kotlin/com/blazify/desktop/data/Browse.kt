package com.blazify.desktop.data

import java.awt.Desktop
import java.net.URI

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Handing a link to whatever the machine uses for links.
 *
 * Java knows how to do this on every desktop it runs on, so ask it first. The
 * hand-rolled fallbacks are for the cases where it does not: a Linux session
 * with no desktop integration, or a headless JVM, where `Desktop` reports
 * itself unsupported and would otherwise throw.
 *
 * On Windows the fallback is `rundll32`, which is fine for a plain address and
 * unreliable for one carrying a query string: an ampersand in a mailto can be
 * swallowed, which is how a bug report arrives with a subject and no body. So
 * `Desktop` handles those, and rundll32 only ever sees what is left.
 */
object Browse {
    private val windows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** A web address. */
    fun open(url: String) {
        if (viaDesktop(url) { desktop, uri -> desktop.browse(uri) }) return
        fallback(url)
    }

    /** A mailto address, which the mail action understands better than the browser one. */
    fun mail(url: String) {
        if (viaDesktop(url) { desktop, uri -> desktop.mail(uri) }) return
        // Some desktops register a browser for mailto and no mail action at all.
        if (viaDesktop(url) { desktop, uri -> desktop.browse(uri) }) return
        fallback(url)
    }

    private inline fun viaDesktop(
        url: String,
        action: (Desktop, URI) -> Unit,
    ): Boolean =
        runCatching {
            if (!Desktop.isDesktopSupported()) return false
            val desktop = Desktop.getDesktop()
            action(desktop, URI(url))
            true
        }.getOrDefault(false)

    private fun fallback(url: String) {
        runCatching {
            if (windows) {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            } else {
                ProcessBuilder("xdg-open", url).start()
            }
        }
    }
}
