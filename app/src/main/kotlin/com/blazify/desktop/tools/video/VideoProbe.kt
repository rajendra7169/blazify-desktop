package com.blazify.desktop.tools.video

import com.blazify.desktop.audio.Letterbox
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Headless
import com.blazify.desktop.data.Track
import kotlinx.coroutines.runBlocking
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The Video look's whole path for one song, without a window.
 *
 * Which video was chosen and why, whether a picture stream came back, and
 * whether frames actually arrive from it — with the black bands measured on a
 * few of them, the way the player measures them before showing anything.
 *
 *   ./gradlew :app:videoProbe --args="Kesariya|Arijit Singh|268"
 *   ./gradlew :app:videoProbe --args="title|artist|seconds|its own id"
 */
fun main(args: Array<String>): Unit = runBlocking {
    val parts = args.joinToString(" ").ifBlank { "Kesariya|Arijit Singh|268" }.split("|")
    val title = parts[0]
    val artist = parts.getOrElse(1) { "" }
    val seconds = parts.getOrNull(2)?.toIntOrNull()
    val id = parts.getOrElse(3) { "probe" }
    val track = Track(id = id, title = title, artist = artist, thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg", durationSeconds = seconds)

    println("choosing a video for $title — $artist (${seconds ?: "?"}s):")
    val videos = Catalogue.musicVideos(track)
    videos.forEach {
        println("  ${it.videoId}, ${it.lengthSeconds}s, ${if (it.inStep) "same cut: kept in step" else "its own cut: plays on its own"}")
    }
    val chosen = videos.firstOrNull()
    if (chosen == null) {
        println("  nothing to show — the cover stays")
        return@runBlocking
    }

    val stream = Catalogue.picture(chosen.videoId).getOrElse {
        println("  no picture stream: ${it.message}")
        Headless.stop()
        return@runBlocking
    }.stream
    println("  picture: ${stream.url.take(96)}…")
    val open = (java.net.URI(stream.url).toURL().openConnection() as java.net.HttpURLConnection).apply {
        setRequestProperty("User-Agent", stream.userAgent)
        setRequestProperty("Range", "bytes=0-65535")
        connectTimeout = 15_000
        readTimeout = 15_000
    }
    println("  fetch: HTTP ${runCatching { open.responseCode }.getOrElse { -1 }}, as ${stream.userAgent.take(40)}")

    val factory = MediaPlayerFactory("--no-audio", "--quiet", "--avcodec-hw=none")
    val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
    var width = 0
    var height = 0
    var pixels = ByteArray(0)
    var frames = 0
    player.videoSurface().set(
        factory.videoSurfaces().newVideoSurface(
            object : BufferFormatCallbackAdapter() {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    width = sourceWidth
                    height = sourceHeight
                    pixels = ByteArray(sourceWidth * sourceHeight * 4)
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {
                    println("  buffer ${bufferWidth}x$bufferHeight, picture ${displayWidth}x$displayHeight")
                }
            },
            object : RenderCallback {
                override fun lock(mediaPlayer: MediaPlayer) {}
                override fun unlock(mediaPlayer: MediaPlayer) {}
                override fun display(
                    mediaPlayer: MediaPlayer,
                    nativeBuffers: Array<ByteBuffer>,
                    bufferFormat: BufferFormat,
                    displayWidth: Int,
                    displayHeight: Int,
                ) {
                    val source = nativeBuffers.first()
                    source.rewind()
                    source.get(pixels, 0, minOf(pixels.size, source.remaining()))
                    source.rewind()
                    frames++
                    if (frames == 2 || frames == 10 || frames == 20 || frames == 32 || frames % 120 == 0) {
                        println("  frame $frames ${width}x$height shown ${displayWidth}x$displayHeight: band ${Letterbox.share(pixels, width, height)}")
                    }
                }
            },
            true,
        ),
    )
    player.media().play(stream.url, ":http-user-agent=${stream.userAgent}", ":no-audio", ":start-time=20")
    Thread.sleep(12_000)
    println("  $frames frames in twelve seconds at ${player.status().time() / 1000.0}s")
    println(if (frames > 0) "  → pictures arrive" else "  → nothing decoded")
    player.controls().stop()
    player.release()
    factory.release()
    Headless.stop()
}
