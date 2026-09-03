package com.blazify.desktop.data

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URLEncoder
import java.util.Locale

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Reporting a problem without leaving the app.
 *
 * There is nothing clever here, and that is the point. Most reports that never
 * arrive are lost at the step where somebody has to find out where to send
 * one, then work out which version they are running, then describe their
 * machine. This fills that in and leaves them the part only they can write.
 *
 * The details are shown before anything is sent. Gathering facts about
 * somebody's computer and posting them unasked is the sort of thing this
 * program exists not to do, even when the facts are dull.
 */
object BugReport {
    private const val OWNER = "rajendra7169"
    private const val REPO = "blazify-desktop"
    private const val EMAIL = "rajendrapandey199971@gmail.com"

    /** Everything that would otherwise be the first three questions of a reply. */
    fun details(): String =
        buildString {
            appendLine("Blazify ${Updates.RUNNING} (desktop)")
            appendLine(
                "${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
                    "(${System.getProperty("os.arch")})",
            )
            appendLine("Java ${System.getProperty("java.version")}")
            append("Language ${Locale.getDefault()}")
        }

    private fun body(): String =
        """
        What happened:


        What you expected instead:


        How to make it happen again:
        1.
        2.

        ---
        ${details()}
        """.trimIndent()

    fun openTracker() {
        val body = encode(body())
        open("https://github.com/$OWNER/$REPO/issues/new?body=$body")
    }

    /**
     * The tracker needs an account and most people who play music do not have
     * one. This asks for nothing but the mail program they already use.
     */
    fun openEmail() {
        val subject = encode("Blazify ${Updates.RUNNING}: ")
        val body = encode(body())
        open("mailto:$EMAIL?subject=$subject&body=$body")
    }

    /** For anyone with neither a mail client nor an account. */
    fun copyDetails() {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(body()), null)
        }
    }

    /** Same approach as the release page: hand it to whatever the desktop uses. */
    private fun open(where: String) {
        runCatching {
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)
            if (windows) {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", where).start()
            } else {
                ProcessBuilder("xdg-open", where).start()
            }
        }
    }

    /** A literal plus is a space to a mail client, so encode it properly. */
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
