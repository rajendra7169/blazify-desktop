package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.runBlocking
import java.io.File
import com.blazify.desktop.audio.StreamFetcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Downloads a slice to disk first, then plays the FILE — isolates the media stack from the network. */
fun main(args: Array<String>) = runBlocking {
    val query = if (args.isEmpty()) "passenger let her go" else args.joinToString(" ")
    val track = Catalogue.search(query).getOrNull()?.firstOrNull() ?: run { println("no results"); return@runBlocking }
    val url = Catalogue.streamUrl(track.id).getOrElse { println("resolve failed: ${it.message}"); return@runBlocking }

    val file = File("/tmp/blazify-probe.m4a")
    println("downloading to ${file.absolutePath}")
    val started = System.currentTimeMillis()
    StreamFetcher.download(url, file) { print("\r  fetching ${(it * 100).toInt()}%") }
        .onFailure { println("\nfetch failed: ${it.message}"); return@runBlocking }
    println("\rfetched: ${file.length() / 1024} KB in ${System.currentTimeMillis() - started} ms")
    println("downloaded ${file.length() / 1024} KB")

    runCatching { Platform.startup { } }
    Platform.setImplicitExit(false)
    val done = CountDownLatch(1)

    Platform.runLater {
        runCatching {
            val media = Media(file.toURI().toString())
            media.setOnError { println("MEDIA error : ${media.error}"); done.countDown() }
            val player = MediaPlayer(media)
            player.setOnError { println("PLAYER error: ${player.error}"); done.countDown() }
            player.setOnReady { println("ready  : ${media.duration?.toSeconds()}s"); player.play() }
            player.setOnPlaying {
                println("PLAYING from file — listen now")
                Thread { Thread.sleep(7000); done.countDown() }.start()
            }
        }.onFailure { println("threw  : ${it::class.simpleName}: ${it.message}"); done.countDown() }
    }

    if (!done.await(40, TimeUnit.SECONDS)) println("no callback at all")
    Platform.exit()
}
