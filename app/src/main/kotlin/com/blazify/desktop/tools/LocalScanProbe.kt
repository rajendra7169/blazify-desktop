package com.blazify.desktop.tools.localscan

import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.LocalMusic
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** Scan a folder, print what the library made of it, and play the first find. */
fun main(args: Array<String>): Unit = runBlocking {
    val folder = File(args.firstOrNull() ?: (System.getProperty("user.home") + "/Music"))
    println("scanning ${folder.absolutePath}")
    LocalMusic.add(folder)
    println("found ${LocalMusic.tracks.size}")
    LocalMusic.tracks.take(10).forEach { println("  ${it.artist}  —  ${it.title}   [${it.id}]") }

    val first = LocalMusic.tracks.firstOrNull() ?: return@runBlocking
    if (!AudioEngine.available()) { println("no native audio library — skipping playback"); return@runBlocking }
    println("playing ${first.title}")
    AudioEngine.play(LocalMusic.pathOf(first.id))
    repeat(6) {
        delay(400)
        println("  %5.2fs of %5.2fs  playing=%s".format(AudioEngine.position, AudioEngine.duration, AudioEngine.playing))
    }
    AudioEngine.stop()
}
