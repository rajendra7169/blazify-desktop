package com.blazify.desktop.tools.account

import com.blazify.desktop.data.Account
import com.blazify.desktop.data.BrowserSession
import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Sign in from a browser and report what the catalogue then says.
 *
 * Prints which browsers were found and whether each carried a session, but
 * never the session itself.
 */
fun main(): Unit = runBlocking {
    val browsers = BrowserSession.installed()
    println("browsers found: " + browsers.joinToString { it.label }.ifBlank { "none" })
    browsers.forEach { browser ->
        val outcome = BrowserSession.sessionFrom(browser)
        println(
            "  %-9s %s".format(
                browser.label,
                outcome.fold({ "carries a session (${it.count { c -> c == ';' } + 1} cookies)" },
                    { "no — ${it.message}" }),
            ),
        )
    }

    Account.signInFromBrowser()
    repeat(40) { if (Account.checking) delay(500) }

    println("signed in : ${Account.signedIn}")
    Account.name?.let { println("account   : $it  ${Account.email ?: ""}") }
    Account.problem?.let { println("problem   : $it") }

    // Asked regardless: the account page and the music pages are different
    // endpoints, and one refusing says nothing about the other.
    Catalogue.mine().fold(
        onSuccess = { println("library   : ${it.size} playlists") },
        onFailure = { println("library   : ${it.message}") },
    )
    Catalogue.home().onSuccess { feed ->
        println("feed      : ${feed.shelves.size} shelves")
        feed.shelves.take(5).forEach { println("   ${it.title}  (${it.cards.size}, rows=${it.rows})") }
    }
}
