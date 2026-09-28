package com.blazify.desktop.tools.engine

import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Headless
import com.blazify.desktop.data.PlayerScript
import com.blazify.desktop.data.Streams
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The whole way from a song's id to bytes arriving, printed step by step.
 *
 * Every step of this can fail for a different reason and they all look the same
 * from the window: the song doesn't play. So each one says what it did — which
 * player script was loaded, what each source answered, whether the address that
 * came out of it actually serves audio. The last part matters most: an address
 * can look perfect and still be refused, and that refusal is the difference
 * between being finished and having one thing left to do.
 *
 *   ./gradlew :app:scriptProbe --args="EJWehzWKz7Y"
 */
fun main(args: Array<String>): Unit = runBlocking {
    val id = args.firstOrNull() ?: "EJWehzWKz7Y"

    println("engine:")
    if (!Headless.possible()) {
        println("  no browser to borrow — ${Headless.trouble ?: "nothing Chromium-based here"}")
        return@runBlocking
    }
    println("  ready: ${Headless.ready()}  borrowed: ${Headless.borrowed}  ${Headless.trouble ?: ""}")

    println("player script:")
    val prepared = PlayerScript.ready()
    println("  ready: $prepared  version: ${PlayerScript.version}  stamp: ${PlayerScript.timestamp}")
    PlayerScript.trouble?.let { println("  trouble: $it") }

    println("resolving $id:")
    val stream = Catalogue.stream(id)
    Streams.Source.entries.forEach { source ->
        Streams.notes[source.name]?.let { println("  ${source.label.padEnd(9)} $it") }
    }

    stream.fold(
        onSuccess = { found ->
            println("  address: ${found.url.take(96)}…")
            println("  as: ${found.userAgent.take(60)}")
            // The one question the rest of this cannot answer: whether the
            // service will actually serve it to us.
            val open = (URI(found.url).toURL().openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", found.userAgent)
                setRequestProperty("Range", "bytes=0-65535")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val code = runCatching { open.responseCode }.getOrElse { -1 }
            val bytes = runCatching { open.inputStream.use { it.readBytes().size } }.getOrDefault(0)
            println("  fetch: HTTP $code, $bytes bytes")
            println(if (bytes > 0) "  → this plays" else "  → refused, so something is still missing")
        },
        onFailure = { println("  failed: ${it.message}") },
    )

    Headless.stop()
}
