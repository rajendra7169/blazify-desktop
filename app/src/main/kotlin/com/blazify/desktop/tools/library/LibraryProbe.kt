package com.blazify.desktop.tools.library

import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** What the account says it holds, printed from a terminal. */
fun main(): Unit = runBlocking {
    Account.restore()
    Account.refresh()
    // The check runs off the main thread, so the probe waits for it the way a
    // window would by simply carrying on redrawing.
    repeat(60) { if (!Account.signedIn) Thread.sleep(500) }
    println("credential: ${Account.hasCredential}  signedIn: ${Account.signedIn}  name: ${Account.name}  problem: ${Account.problem}")

    Catalogue.mine()
        .onSuccess { cards ->
            println("\nlibrary: ${cards.size} items")
            cards.groupBy { it.kind }.forEach { (kind, group) ->
                println("  $kind: ${group.size}")
                group.take(4).forEach { println("    - ${it.title} [${it.id}]") }
            }
        }
        .onFailure { println("\nlibrary failed: $it") }

    Catalogue.myPlaylists()
        .onSuccess { lists ->
            println("\neditable playlists: ${lists.size}")
            lists.forEach { println("  - ${it.name} [${it.id}] ${it.count}") }
        }
        .onFailure { println("\nplaylists failed: $it") }
}
