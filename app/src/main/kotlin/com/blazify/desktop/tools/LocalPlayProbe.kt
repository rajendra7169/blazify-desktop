package com.blazify.desktop.tools.localplay

import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.audio.StreamFetcher
import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Plays a track for a few seconds, first straight from the network and then
 * from a downloaded copy, reporting which route works.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val query = if (args.isEmpty()) "passenger let her go" else args.joinToString(" ")

    if (!AudioEngine.available()) {
        println("the native audio library isn't installed — run: sudo apt install vlc libvlc-dev")
        return@runBlocking
    }

    val track = Catalogue.search(query).getOrNull()?.firstOrNull()
        ?: run { println("no results for \"$query\""); return@runBlocking }
    println("track  : ${track.title} — ${track.artist}")

    val url = Catalogue.streamUrl(track.id).getOrElse {
        println("resolve: failed — ${it.message}"); return@runBlocking
    }
    println("resolve: ok")

    suspend fun attempt(label: String, mrl: String) {
        println("\n$label")
        AudioEngine.play(mrl)
        repeat(24) {
            delay(500)
            if (AudioEngine.playing) {
                println("  PLAYING — ${"%.1f".format(AudioEngine.position)}s of ${"%.0f".format(AudioEngine.duration)}s")
                if (AudioEngine.position > 3) { println("  sound is coming out"); return }
            }
            AudioEngine.error?.let { println("  failed: $it"); return }
        }
        println("  no sound after 12s")
    }

    attempt("direct from the network:", url)
    AudioEngine.stop()

    val file = File("/tmp/blazify-probe.m4a")
    StreamFetcher.download(url, file).onSuccess {
        println("\ndownloaded ${file.length() / 1024} KB")
        attempt("from the downloaded copy:", file.absolutePath)
    }
    AudioEngine.stop()
}
