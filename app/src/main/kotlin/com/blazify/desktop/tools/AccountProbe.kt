package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.GoogleSignIn
import com.blazify.desktop.data.Store
import com.blazify.innertube.YouTube
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Check that a signed-in account is actually accepted.
 *
 * Uses the token already on this machine rather than signing in again, and
 * prints nothing from it beyond whether it worked and whose it is.
 */
fun main() = runBlocking {
    val stored = File(Store.folder, "account-refresh").takeIf { it.exists() }?.readText()?.trim()
    if (stored.isNullOrBlank()) {
        println("nobody is signed in on this machine")
        return@runBlocking
    }

    val tokens = GoogleSignIn.refresh(stored).getOrElse {
        println("couldn't renew the token: ${it.message}")
        return@runBlocking
    }
    println("token renewed, good for ${tokens.expiresInSeconds}s")
    YouTube.accessToken = tokens.access

    YouTube.accountInfo().fold(
        onSuccess = { println("account : ${it.name}  ${it.email ?: ""}") },
        onFailure = { println("account : REFUSED — ${it.message}") },
    )

    Catalogue.mine().fold(
        onSuccess = { println("library : ${it.size} playlists") },
        onFailure = { println("library : REFUSED — ${it.message}") },
    )

    Catalogue.home().fold(
        onSuccess = { feed ->
            println("feed    : ${feed.shelves.size} shelves")
            feed.shelves.take(6).forEach {
                println("   ${it.title}  (${it.cards.size}, rows=${it.rows})")
            }
        },
        onFailure = { println("feed    : REFUSED — ${it.message}") },
    )
}
