package com.blazify.desktop.tools

import com.blazify.desktop.data.GoogleSignIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Walk through signing in from a terminal.
 *
 * Prints the code and the page to enter it on, then waits. Approving it is
 * something only the person with the account can do, so this either finishes
 * when they do, or gives up after a couple of minutes.
 */
fun main() = runBlocking {
    val request = GoogleSignIn.request().getOrElse {
        println("couldn't ask Google for a code: ${it.message}")
        return@runBlocking
    }
    println("code    : ${request.userCode}")
    println("page    : ${request.url}")
    println("waiting : polling every ${request.intervalSeconds}s\n")

    val outcome = withTimeoutOrNull(120_000) { GoogleSignIn.await(request) }
    when {
        outcome == null -> println("nobody approved it within two minutes — the exchange itself is fine")
        outcome.isSuccess -> {
            val tokens = outcome.getOrThrow()
            println("signed in: access token ${tokens.access.take(12)}…, expires in ${tokens.expiresInSeconds}s")
            println("refresh token present: ${tokens.refresh.isNotBlank()}")
        }
        else -> println("failed: ${outcome.exceptionOrNull()?.message}")
    }
}
