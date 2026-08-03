package com.blazify.desktop.tools.sound

import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.runBlocking

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Resolve one song and actually play it, printing what the engine reports.
 *
 * The point is to separate "the catalogue won't give us audio" from "the audio
 * arrives and something here refuses to play it" — two failures that look
 * identical from a window with a silent play button.
 */
fun main(args: Array<String>): Unit = runBlocking {
    Account.restore()
    val query = args.joinToString(" ").ifBlank { "tum hi ho" }

    val found = Catalogue.search(query).getOrNull().orEmpty()
    val track = found.firstOrNull()
    if (track == null) {
        println("nothing found for \"$query\"")
        return@runBlocking
    }
    println("playing ${track.title} — ${track.artist}")

    println("engine available: ${AudioEngine.available()}")

    // Through the app's own path, not straight to the engine — that is where
    // the difference between "the audio works" and "the app plays" lives.
    if (args.contains("--app")) {
        com.blazify.desktop.PlayerState.play(listOf(track), 0)
        repeat(15) {
            Thread.sleep(1000)
            println(
                "%2ds  playing=%-5s buffering=%-5s pos=%.1f vol=%.2f fail=%s".format(
                    it + 1,
                    com.blazify.desktop.PlayerState.playing,
                    AudioEngine.buffering,
                    com.blazify.desktop.PlayerState.positionSeconds,
                    com.blazify.desktop.PlayerState.volume,
                    com.blazify.desktop.PlayerState.failure ?: "-",
                ),
            )
        }
        return@runBlocking
    }
    val stream = Catalogue.stream(track.id).getOrNull()
    if (stream == null) {
        println("no stream url")
        return@runBlocking
    }
    println("got a url (${stream.url.length} chars) as ${stream.userAgent.take(40)}…")

    AudioEngine.play(stream.url, stream.userAgent)
    AudioEngine.setVolume(1.0)

    repeat(20) {
        Thread.sleep(1000)
        println(
            "%2ds  playing=%-5s loading=%-5s buffering=%-5s stalled=%-5s pos=%.1f dur=%.1f err=%s".format(
                it + 1,
                AudioEngine.playing, AudioEngine.loading, AudioEngine.buffering, AudioEngine.stalled,
                AudioEngine.position, AudioEngine.duration, AudioEngine.error ?: "-",
            ),
        )
    }
    AudioEngine.stop()
}
