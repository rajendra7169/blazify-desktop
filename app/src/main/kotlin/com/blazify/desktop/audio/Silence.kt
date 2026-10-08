package com.blazify.desktop.audio

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Finds the quiet stretches in a song: the long hush before the first note,
 * the minute of nothing before a hidden track, the fade that went on after
 * everybody stopped listening.
 *
 * The player can't be asked about loudness while it plays — listening in on
 * its audio means taking over its output, which is the one thing it must keep.
 * So a song that is already on disk is read through a second, silent copy of
 * the same library, turned into a small file of plain samples — mono, eight
 * thousand a second, plenty to hear whether anything is happening — and that
 * file is measured here. Far quicker than playing it: a four-minute song is
 * read in well under a second.
 *
 * Only files on disk. A stream would mean fetching the whole song a second
 * time beside the copy that is playing, which is exactly the competition the
 * player can't afford.
 */
object Silence {

    /** A stretch with nothing in it, in seconds from the start. */
    data class Gap(val from: Double, val to: Double)

    private const val RATE = 8000

    /**
     * Quieter than this is nothing. About fifty decibels below full scale —
     * under the hiss of a vinyl rip, over the dither of a digital fade.
     */
    private const val FLOOR = 100.0

    /** Leading and trailing hush counts from a second; anything less is breath. */
    private const val EDGE = 1.0

    /** In the middle, only a real gap: a pause for effect is part of the song. */
    private const val MIDDLE = 3.0

    private val factory: MediaPlayerFactory? by lazy {
        // The main player finds the library first, including the copy that
        // travels with the application where there is one.
        if (!AudioEngine.available()) return@lazy null
        runCatching { MediaPlayerFactory("--quiet", "--no-video", "--no-sout-video") }.getOrNull()
    }

    private val lock = Any()

    /**
     * The quiet stretches in [file], or none if it couldn't be read.
     *
     * Blocking, and one at a time. Called from a background thread, never
     * from anything that is waiting on it.
     */
    fun find(file: File, work: File): List<Gap> = synchronized(lock) {
        try {
            read(file, work)
        } catch (_: Exception) {
            emptyList()
        } finally {
            work.delete()
        }
    }

    private fun read(file: File, work: File): List<Gap> {
        val made = factory ?: return emptyList()
        work.parentFile?.mkdirs()
        work.delete()
        val target = work.absolutePath.replace('\\', '/')
        val player: MediaPlayer = made.mediaPlayers().newMediaPlayer()
        try {
            val done = CountDownLatch(1)
            player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun finished(mediaPlayer: MediaPlayer) = done.countDown()
                override fun error(mediaPlayer: MediaPlayer) = done.countDown()
                override fun stopped(mediaPlayer: MediaPlayer) = done.countDown()
            })
            player.media().play(
                file.absolutePath,
                ":sout=#transcode{acodec=s16l,channels=1,samplerate=$RATE}" +
                    ":std{access=file,mux=wav,dst=\"$target\"}",
                ":no-sout-video",
                ":sout-keep=false",
            )
            if (!done.await(60, TimeUnit.SECONDS)) return emptyList()
        } finally {
            runCatching { player.controls().stop() }
            runCatching { player.release() }
        }
        return gaps(samples(work.readBytes()))
    }

    /** The samples in a plain sixteen-bit mono wave file. */
    private fun samples(bytes: ByteArray): ShortArray {
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, at + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                val start = at + 8
                // A file written in one go sometimes leaves the size at zero.
                val length = if (size <= 0) bytes.size - start else minOf(size, bytes.size - start)
                val out = ShortArray(length / 2)
                ByteBuffer.wrap(bytes, start, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
                return out
            }
            at += 8 + size.coerceAtLeast(0) + (size and 1)
        }
        return ShortArray(0)
    }

    /**
     * Where the quiet is, from the samples.
     *
     * Each window — fifty milliseconds, short enough to find edges and long
 * enough to average — is called quiet or not by its average level. A run of quiet
     * windows touching the start or the end counts from [EDGE]; one in the
     * middle only from [MIDDLE]. A little is left at each side of a gap in
     * the middle, so the music either side of it doesn't arrive clipped.
     */
    fun gaps(pcm: ShortArray, rate: Int = RATE): List<Gap> {
        val window = rate / 20
        val windows = pcm.size / window
        if (windows == 0) return emptyList()
        val quiet = BooleanArray(windows) { w ->
            var sum = 0.0
            for (i in w * window until (w + 1) * window) {
                val v = pcm[i].toDouble()
                sum += v * v
            }
            sqrt(sum / window) < FLOOR
        }
        val step = window.toDouble() / rate
        val length = windows * step
        val found = mutableListOf<Gap>()
        var w = 0
        while (w < windows) {
            if (!quiet[w]) { w++; continue }
            val first = w
            while (w < windows && quiet[w]) w++
            val from = first * step
            val to = w * step
            val span = to - from
            when {
                first == 0 && span >= EDGE -> found += Gap(0.0, (to - 0.15).coerceAtLeast(0.0))
                w == windows && span >= EDGE -> found += Gap(from + 0.15, length)
                span >= MIDDLE -> found += Gap(from + 0.5, to - 0.5)
            }
        }
        return found
    }
}
