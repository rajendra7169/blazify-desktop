package com.blazify.desktop.tools.liked

import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Library
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Sign in, fetch the account's liked songs, and report what came back. */
fun main(): Unit = runBlocking {
    Account.signInFromBrowser()
    repeat(40) { if (Account.checking) delay(500) }
    println("signed in : ${Account.signedIn}  ${Account.name ?: ""}")
    Account.problem?.let { println("problem   : $it") }
    if (!Account.signedIn) return@runBlocking

    Catalogue.myLikedSongs().fold(
        onSuccess = { songs ->
            println("account   : ${songs.size} liked songs")
            songs.take(6).forEach { println("   ${it.title} — ${it.artist}") }
        },
        onFailure = { println("account   : FAILED — ${it.message}") },
    )

    println("on disk   : ${Library.liked.size} before sync")
    Library.syncWithAccount()
    repeat(40) { if (Library.syncing) delay(500) }
    println("on disk   : ${Library.liked.size} after sync")
}
