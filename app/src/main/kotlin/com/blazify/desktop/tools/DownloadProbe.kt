package com.blazify.desktop.tools.download

import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Downloads
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Keep a song for offline, then play the kept copy with the network unused. */
fun main(args: Array<String>): Unit = runBlocking {
    val query = if (args.isEmpty()) "passenger let her go" else args.joinToString(" ")
    val track = Catalogue.search(query).getOrNull()?.firstOrNull()
        ?: run { println("no results for \"$query\""); return@runBlocking }
    println("track : ${track.title} — ${track.artist}")

    Downloads.start(track)
    var waited = 0
    while (Downloads.isRunning(track.id) && waited < 120) {
        delay(500)
        waited += 1
        if (waited % 4 == 0) println("  %.0f%%".format(Downloads.progressOf(track.id) * 100))
    }

    val file = Downloads.fileFor(track.id)
    println("kept  : ${Downloads.has(track.id)}  ${file.length() / 1_000_000.0} MB")
    Downloads.failure?.let { println("failed: $it") }
    if (!Downloads.has(track.id)) return@runBlocking

    if (!AudioEngine.available()) { println("no native audio library"); return@runBlocking }
    AudioEngine.play(file.absolutePath)
    repeat(4) {
        delay(500)
        println("  %5.2fs of %5.2fs".format(AudioEngine.position, AudioEngine.duration))
    }
    AudioEngine.stop()
}
