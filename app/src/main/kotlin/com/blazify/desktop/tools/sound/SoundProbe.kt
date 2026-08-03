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

    // A timed run: how long before the first sound, and how long a jump into
    // the middle takes to come back. Both are the numbers that decide whether a
    // long recording feels broken.
    if (args.contains("--time")) {
        val began = System.currentTimeMillis()
        com.blazify.desktop.PlayerState.play(listOf(track), 0)

        val started = waitForAudio(began, 60_000)
        if (started < 0) {
            println("  never started (60s)")
            return@runBlocking
        }
        println("  first sound after %.1fs".format(started / 1000.0))

        val length = AudioEngine.duration
        println("  length %d:%02d".format((length / 60).toInt(), (length % 60).toInt()))

        // Four jumps at random, which is what somebody actually does to a long
        // recording — they do not scrub politely from front to back.
        val seed = track.id.hashCode().toLong()
        val dice = java.util.Random(seed)
        repeat(4) { round ->
            val where = 0.05 + dice.nextDouble() * 0.85
            val at = where * length
            val before = AudioEngine.position
            val jumped = System.currentTimeMillis()
            com.blazify.desktop.PlayerState.seek(where.toFloat())

            val back = waitForMove(at, jumped, 45_000)
            if (back < 0) {
                println(
                    "  jump %d to %d:%02d — did not resume within 45s (was at %.0fs)".format(
                        round + 1, (at / 60).toInt(), (at % 60).toInt(), before,
                    ),
                )
            } else {
                println(
                    "  jump %d to %d:%02d — playing again after %.1fs".format(
                        round + 1, (at / 60).toInt(), (at % 60).toInt(), back / 1000.0,
                    ),
                )
            }
            Thread.sleep(3000)
        }
        AudioEngine.stop()
        return@runBlocking
    }

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

/** Milliseconds until the position first moves, or -1 if it never does. */
private fun waitForAudio(from: Long, limit: Long): Long {
    while (System.currentTimeMillis() - from < limit) {
        Thread.sleep(100)
        if (AudioEngine.position > 0.3 && !AudioEngine.buffering) {
            return System.currentTimeMillis() - from
        }
        AudioEngine.error?.let { return -1 }
    }
    return -1
}

/** Milliseconds until playback is moving again near [target]. */
private fun waitForMove(target: Double, from: Long, limit: Long): Long {
    var seen = -1.0
    while (System.currentTimeMillis() - from < limit) {
        Thread.sleep(200)
        val now = AudioEngine.position
        // Near where it was asked to go, and actually advancing from there.
        if (kotlin.math.abs(now - target) < 20 && seen > 0 && now > seen && !AudioEngine.buffering) {
            return System.currentTimeMillis() - from
        }
        if (kotlin.math.abs(now - target) < 20) seen = now
    }
    return -1
}
