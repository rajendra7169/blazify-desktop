package com.blazify.desktop.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Pulls an audio stream down to a file.
 *
 * Two things about the server make this less obvious than it looks:
 *
 *  - It refuses an OPEN-ENDED range. `bytes=0-` comes back 403 while
 *    `bytes=0-1048575` comes back 206, so every request has to name an end.
 *    That single fact is why a player can't be handed the URL directly — every
 *    media framework asks for the whole file first.
 *  - A single sequential pass is throttled hard. Asking for several chunks at
 *    once is dramatically faster for the same bytes.
 *
 * So: fixed-size chunks, several in flight, written straight into their place
 * in the file.
 */
object StreamFetcher {

    private const val CHUNK = 1L shl 20      // 1 MiB — larger ranges get refused
    private const val PARALLEL = 6

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    /** How far along a download is, 0..1. */
    fun interface Progress {
        fun onProgress(fraction: Float)
    }

    suspend fun download(url: String, into: File, progress: Progress? = null): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val total = contentLength(url)
                require(total > 0) { "The server didn't say how big the track is" }

                into.parentFile?.mkdirs()
                RandomAccessFile(into, "rw").use { out -> out.setLength(total) }

                val ranges = buildList {
                    var start = 0L
                    while (start < total) {
                        add(start to minOf(start + CHUNK - 1, total - 1))
                        start += CHUNK
                    }
                }

                var done = 0
                ranges.chunked(PARALLEL).forEach { batch ->
                    coroutineScope {
                        batch.map { (from, to) ->
                            async { fetchRange(url, from, to, into) }
                        }.awaitAll()
                    }
                    done += batch.size
                    progress?.onProgress(done.toFloat() / ranges.size)
                }
                into
            }
        }

    private fun contentLength(url: String): Long {
        // A tiny bounded request, purely to read the total out of Content-Range.
        val response = http.newCall(
            Request.Builder().url(url).header("Range", "bytes=0-1").build(),
        ).execute()
        response.use {
            val header = it.header("Content-Range") ?: return -1
            return header.substringAfter('/').toLongOrNull() ?: -1
        }
    }

    private fun fetchRange(url: String, from: Long, to: Long, into: File) {
        val response = http.newCall(
            Request.Builder().url(url).header("Range", "bytes=$from-$to").build(),
        ).execute()
        response.use {
            check(it.isSuccessful) { "Chunk $from-$to came back ${it.code}" }
            val bytes = it.body?.bytes() ?: error("Chunk $from-$to was empty")
            RandomAccessFile(into, "rw").use { file ->
                file.seek(from)
                file.write(bytes)
            }
        }
    }
}
