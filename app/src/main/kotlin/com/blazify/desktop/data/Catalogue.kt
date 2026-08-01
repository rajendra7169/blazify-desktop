package com.blazify.desktop.data

import com.blazify.innertube.YouTube
import com.blazify.innertube.models.SongItem
import com.blazify.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** One song, in the shape the screens actually use. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val durationSeconds: Int?,
) {
    val duration: String
        get() = durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: ""
}

/**
 * Everything the app asks of the catalogue, behind one door.
 *
 * Screens never touch the client directly — they get [Track]s and a URL, and
 * stay unaware of clients, formats and itags.
 */
object Catalogue {

    suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).map { result ->
            result.items.filterIsInstance<SongItem>().map { song ->
                Track(
                    id = song.id,
                    title = song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    thumbnail = song.thumbnail,
                    durationSeconds = song.duration,
                )
            }
        }
    }

    /**
     * A playable audio URL for a song.
     *
     * Tries each client in turn: the first two answer for music without a proof
     * token, and the third is there for the handful they refuse. Only AAC in an
     * MP4 container is considered — the alternatives come back in containers the
     * player can't open, so a higher bitrate in the wrong format is no use.
     */
    suspend fun streamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        val clients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.VISIONOS,
            YouTubeClient.IOS,
        )

        for (client in clients) {
            val response = YouTube.player(videoId, client = client).getOrNull() ?: continue
            if (response.playabilityStatus.status != "OK") continue

            val best = response.streamingData
                ?.adaptiveFormats
                ?.filter { it.mimeType.startsWith("audio/mp4") && !it.url.isNullOrEmpty() }
                ?.maxByOrNull { it.bitrate }
                ?: continue

            best.url?.let { return@withContext Result.success(it) }
        }
        Result.failure(IllegalStateException("No playable audio for $videoId"))
    }
}
