package com.blazify.desktop.tools.session

import com.blazify.desktop.data.BrowserSession
import com.blazify.innertube.YouTube
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What the browser gave us, and what the catalogue made of it.
 *
 * Cookie NAMES only — never their values. The names are enough to tell a
 * missing credential from a rejected one, and printing the values would be
 * printing the account itself into a terminal and a scrollback buffer.
 */
fun main(): Unit = runBlocking {
    val browsers = BrowserSession.installed()
    println("browsers with a cookie store: ${browsers.joinToString { it.label }}\n")

    for (browser in browsers) {
        val session = BrowserSession.sessionFrom(browser)
        session.fold(
            onSuccess = { cookies ->
                val names = cookies.split("; ").mapNotNull { it.substringBefore('=').takeIf(String::isNotBlank) }
                println("${browser.label}: ${names.size} cookies")
                println("  ${names.sorted().joinToString(", ")}")

                // The ones the catalogue's own auth is built from.
                val needed = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PSID", "__Secure-3PSID")
                val missing = needed.filterNot { it in names }
                println("  missing of the essentials: ${missing.ifEmpty { listOf("none") }.joinToString()}")

                YouTube.cookie = cookies
                // As the app does when it attaches a session: an anonymous
                // visitor id minted before sign-in contradicts the account it
                // would then be sent with.
                YouTube.visitorData = null
                YouTube.useLoginForBrowse = true
                // Proof the renewal is being caught rather than dropped: the
                // site rotates the security cookies on almost every reply.
                var renewed = 0
                YouTube.onCookieRefreshed = { renewed += 1 }

                YouTube.accountInfo().fold(
                    onSuccess = { println("  ACCEPTED — ${it.name}") },
                    onFailure = { println("  REFUSED — ${it.javaClass.simpleName}: ${it.message?.take(200)}") },
                )
                // A second, unrelated authenticated request. If the library
                // answers with somebody's own shelves then the session is good
                // and only the account menu has changed shape; if this is
                // refused too, the session itself is what Google won't take.
                YouTube.library("FEmusic_liked_playlists").fold(
                    onSuccess = { println("  library — ${it.items.size} shelves of my own") },
                    onFailure = { println("  library — refused (${it.javaClass.simpleName})") },
                )
                println("  the site handed back a newer session $renewed times")
            },
            onFailure = { println("${browser.label}: could not read — ${it.message}") },
        )
        println()
    }
}
