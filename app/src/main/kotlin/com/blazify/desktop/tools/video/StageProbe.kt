package com.blazify.desktop.tools.video

import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.audio.VideoEngine
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Track
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The Video look's real engine beside a song really playing, without a window.
 *
 * Quietly, since the point is the pictures. Prints every two seconds whether
 * frames are arriving, whether the picture would be on screen, and how much of
 * it changed since the last look: a video that is one still image plays
 * perfectly and looks exactly like no video at all.
 *
 *   ./gradlew :app:stageProbe --args="title|artist|seconds|id"
 */
fun main(args: Array<String>): Unit = runBlocking {
    val parts = args.joinToString(" ").split("|")
    val track = Track(
        id = parts[3], title = parts[0], artist = parts[1], thumbnail = null, durationSeconds = parts[2].toIntOrNull(),
    )
    println("song: ${track.title} — ${track.artist} [${track.id}] ${track.durationSeconds}s")
    Catalogue.musicVideos(track).forEach { println("  candidate ${it.videoId} ${it.lengthSeconds}s inStep=${it.inStep}") }
    val stream = Catalogue.stream(track.id).getOrThrow()
    AudioEngine.play(stream.url, stream.userAgent)
    AudioEngine.setVolume(0.02)
    VideoEngine.show(track)

    var lastFrames = 0L
    var before: ByteArray? = null
    repeat(13) {
        Thread.sleep(2000)
        val frames = VideoEngine.frames
        val now = VideoEngine.snapshot()?.use { image -> Bitmap.makeFromImage(image).readPixels() }
        val changed = if (now != null && before != null && now.size == before!!.size) {
            val step = 4 * 37
            val looked = now.size / step
            (0 until looked).count { i -> kotlin.math.abs(now[i * step] - before!![i * step]) > 12 } * 100 / looked.coerceAtLeast(1)
        } else {
            -1
        }
        before = now
        println(
            "%2ds song=%.1f video=%s +%d frames revealed=%-5s %dx%d changed=%d%%".format(
                (it + 1) * 2, AudioEngine.position, VideoEngine.playingVideo, frames - lastFrames,
                VideoEngine.revealed, VideoEngine.pictureWidth, VideoEngine.pictureHeight, changed,
            ),
        )
        lastFrames = frames
    }
    AudioEngine.stop()
    VideoEngine.hide()
    Thread.sleep(500)
    kotlin.system.exitProcess(0)
}
