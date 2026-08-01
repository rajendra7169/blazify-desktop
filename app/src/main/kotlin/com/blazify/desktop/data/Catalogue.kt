package com.blazify.desktop.data

import com.blazify.innertube.YouTube
import com.blazify.innertube.models.SongItem
import com.blazify.innertube.pages.HomePage
import com.blazify.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val identityLock = Mutex()

    /**
     * Fetch the visitor identity once, and keep it.
     *
     * Without it the good clients refuse outright — one asks for a login, the
     * other calls every track unplayable — and the only one left over hands back
     * a URL that stops dead at exactly one megabyte. With it they answer
     * properly. It costs a single request on the first play of a session.
     */
    private suspend fun ensureIdentity() {
        if (YouTube.visitorData != null) return
        identityLock.withLock {
            if (YouTube.visitorData != null) return
            YouTube.visitorData().getOrNull()?.let { YouTube.visitorData = it }
        }
    }

    suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).map { result ->
            result.items.filterIsInstance<SongItem>().map { it.asTrack() }
        }
    }

    /** One shelf of the feed: a heading and the songs under it. */
    data class Shelf(val title: String, val tracks: List<Track>)

    /**
     * The feed, reduced to shelves of songs.
     *
     * The catalogue mixes songs, albums, artists and playlists into the same
     * carousels. Only songs can be played from a shelf without another request,
     * so anything else is dropped and a shelf left empty goes with it.
     */
    suspend fun home(): Result<List<Shelf>> = withContext(Dispatchers.IO) {
        ensureIdentity()
        YouTube.home().map { page ->
            page.sections.mapNotNull { section ->
                val songs = section.items.filterIsInstance<SongItem>().map { it.asTrack() }
                if (songs.size < 3) null else Shelf(section.title, songs)
            }
        }
    }

    private fun SongItem.asTrack() = Track(
        id = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        thumbnail = thumbnail,
        durationSeconds = duration,
    )

    /**
     * A playable audio URL for a song.
     *
     * Tries each client in turn: the first two answer for music without a proof
     * token, and the third is there for the handful they refuse. Only AAC in an
     * MP4 container is considered — the alternatives come back in containers the
     * player can't open, so a higher bitrate in the wrong format is no use.
     */
    suspend fun streamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        ensureIdentity()
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
