package com.blazify.desktop.tools.longsong

import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Streams
import com.blazify.innertube.YouTube
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What the catalogue actually offers for one recording, and how fast it hands
 * it over.
 *
 * Long recordings are the ones that stall, and the useful question is not "does
 * our code work" but "what were we given and at what speed" — a forty-minute
 * file arriving at fifty kilobytes a second takes eleven minutes to buffer,
 * which is indistinguishable from broken.
 *
 *   ./gradlew :app:longProbe --args="VIDEO_ID"
 */
fun main(args: Array<String>): Unit = runBlocking {
    Account.restore()
    val id = args.firstOrNull() ?: "5qap5aO4i9A"
    println("asking about $id\n")

    for (source in Streams.Source.entries) {
        val response = YouTube.player(id, client = source.client).getOrNull()
        if (response == null) {
            println("%-10s no answer".format(source.label))
            continue
        }
        val status = response.playabilityStatus.status
        val streaming = response.streamingData
        val audio = streaming?.adaptiveFormats
            ?.filter { it.mimeType.startsWith("audio/") && !it.url.isNullOrEmpty() }
            .orEmpty()

        val length = response.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0
        println(
            "%-10s %-4s  %d audio formats  hls=%s  dash=%s  length=%dm%02ds".format(
                source.label,
                status,
                audio.size,
                if (streaming?.hlsManifestUrl != null) "yes" else "no",
                if (streaming?.dashManifestUrl != null) "yes" else "no",
                length / 60, length % 60,
            ),
        )

        audio.sortedByDescending { it.bitrate }.forEach { format ->
            println(
                "           itag=%-4d %-28s %dkbps%s".format(
                    format.itag,
                    format.mimeType.substringBefore(';'),
                    format.bitrate / 1000,
                    if (format.url.isNullOrEmpty()) "  (no url — ciphered)" else "",
                ),
            )
        }

        val best = audio.filter { it.mimeType.startsWith("audio/mp4") }.maxByOrNull { it.bitrate }
        if (best?.url != null) {
            val bytes = best.contentLength ?: 0
            println(
                "           best itag=%d %dkbps %s".format(
                    best.itag, best.bitrate / 1000,
                    if (bytes > 0) "${bytes / 1_048_576}MB" else "size unknown",
                ),
            )
            // How fast the first slice arrives. This is the number that decides
            // whether a long recording plays or sits at zero.
            val speed = throughput(best.url!!, 0, 524_287)
            println("           first 512KB at ${speed}KB/s")

            // The number that decides whether a long recording survives. The
            // opening burst is generous on purpose; what matters is the rate a
            // few minutes in, once the throttle has had time to apply.
            if (bytes > 2_000_000) {
                val middle = bytes / 2
                val to = middle + 2_097_151

                val agent = source.client.userAgent
                println("           middle, no user-agent:      ${describe(best.url!!, middle, to, null)}")
                println("           middle, client user-agent:  ${describe(best.url!!, middle, to, agent)}")
            }
        }
        println()
    }
}

/** What actually comes back, and how fast. */
private fun describe(url: String, from: Long, to: Long, agent: String?): String = runCatching {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.setRequestProperty("Range", "bytes=$from-$to")
    agent?.let { connection.setRequestProperty("User-Agent", it) }
    connection.connectTimeout = 8000
    connection.readTimeout = 30000
    val started = System.currentTimeMillis()
    val code = connection.responseCode
    val read = connection.inputStream.use { it.readBytes().size }
    val took = (System.currentTimeMillis() - started).coerceAtLeast(1)
    connection.disconnect()
    "HTTP $code, ${read / 1024}KB in ${took}ms (${read / 1024L * 1000 / took}KB/s)"
}.getOrElse { "failed: ${it.javaClass.simpleName} ${it.message}" }

/** Straight through from the start, as a player without ranges would read it. */
private fun sequential(url: String): String = runCatching {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 30000
    val started = System.currentTimeMillis()
    var total = 0L
    connection.inputStream.use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (total < 3_145_728) {
            val read = stream.read(buffer)
            if (read <= 0) break
            total += read
        }
    }
    val took = (System.currentTimeMillis() - started).coerceAtLeast(1)
    connection.disconnect()
    "${total / 1024}KB in ${took}ms (${total / 1024L * 1000 / took}KB/s)"
}.getOrElse { "failed: ${it.javaClass.simpleName} ${it.message}" }

/** Kilobytes a second, measured on the first half-megabyte. */
private fun throughput(url: String, from: Long, to: Long): Long = runCatching {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.setRequestProperty("Range", "bytes=$from-$to")
    connection.connectTimeout = 8000
    connection.readTimeout = 30000
    val started = System.currentTimeMillis()
    val read = connection.inputStream.use { it.readBytes().size }
    val took = (System.currentTimeMillis() - started).coerceAtLeast(1)
    connection.disconnect()
    read / 1024L * 1000 / took
}.getOrDefault(0L)
