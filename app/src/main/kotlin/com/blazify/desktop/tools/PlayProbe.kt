package com.blazify.desktop.tools

import com.blazify.desktop.data.Catalogue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Resolves a stream and tries to play it, printing whatever the engine says. */
fun main(args: Array<String>) = runBlocking {
    val query = if (args.isEmpty()) "passenger let her go" else args.joinToString(" ")

    val track = Catalogue.search(query).getOrNull()?.firstOrNull()
    if (track == null) { println("no results for \"$query\""); return@runBlocking }
    println("track : ${track.title} — ${track.artist}")

    val url = Catalogue.streamUrl(track.id).getOrElse {
        println("resolve failed: ${it.message}"); return@runBlocking
    }
    println("url   : ${url.take(90)}…")

    runCatching { Platform.startup { } }
    Platform.setImplicitExit(false)

    val done = CountDownLatch(1)
    Platform.runLater {
        runCatching {
            val media = Media(url)
            media.setOnError { println("MEDIA error : ${media.error}"); done.countDown() }
            val player = MediaPlayer(media)
            player.setOnError { println("PLAYER error: ${player.error}"); done.countDown() }
            player.setOnReady {
                println("ready  : duration=${media.duration?.toSeconds()}s tracks=${media.tracks.size}")
                player.play()
            }
            player.setOnPlaying {
                println("PLAYING — you should hear sound now")
                Thread { Thread.sleep(6000); done.countDown() }.start()
            }
        }.onFailure { println("threw  : ${it::class.simpleName}: ${it.message}"); done.countDown() }
    }

    if (!done.await(35, TimeUnit.SECONDS)) println("timed out with no callback at all")
    Platform.exit()
}
