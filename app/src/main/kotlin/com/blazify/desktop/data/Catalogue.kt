package com.blazify.desktop.data

import com.blazify.innertube.YouTube
import com.blazify.innertube.models.AlbumItem
import com.blazify.innertube.models.ArtistItem
import com.blazify.innertube.models.PlaylistItem
import com.blazify.innertube.models.SongItem
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

    /** What a card on a shelf stands for, which decides what opening it does. */
    enum class Kind { Song, Album, Playlist, Artist }

    /** One tile on a shelf. */
    data class Card(
        val id: String,
        val title: String,
        val subtitle: String,
        val thumbnail: String?,
        val kind: Kind,
    )

    /** One shelf of the feed: a heading and the tiles under it. */
    data class Shelf(val title: String, val cards: List<Card>) {
        /** Songs are drawn as compact lines; everything else as artwork cards. */
        val isSongs: Boolean get() = cards.isNotEmpty() && cards.all { it.kind == Kind.Song }
    }

    /** A page of shelves, plus the token that fetches the next lot. */
    data class Feed(val shelves: List<Shelf>, val more: String?)

    /**
     * The feed.
     *
     * Signed out it comes back as playlists and albums rather than individual
     * songs, so shelves are tiles rather than rows — which is also what a wide
     * window has the space for.
     */
    suspend fun home(after: String? = null): Result<Feed> = withContext(Dispatchers.IO) {
        ensureIdentity()
        YouTube.home(continuation = after).map { page ->
            Feed(
                shelves = page.sections.mapNotNull { section ->
                    val cards = section.items.mapNotNull { it.asCard() }
                    if (cards.isEmpty()) null else Shelf(section.title, cards)
                },
                more = page.continuation,
            )
        }
    }

    private fun com.blazify.innertube.models.YTItem.asCard(): Card? = when (this) {
        is SongItem -> Card(id, title, artists.joinToString(", ") { it.name }, thumbnail, Kind.Song)
        is AlbumItem -> Card(id, title, artists?.joinToString(", ") { it.name } ?: "Album", thumbnail, Kind.Album)
        is PlaylistItem -> Card(id, title, author?.name ?: "Playlist", thumbnail, Kind.Playlist)
        is ArtistItem -> Card(id, title, "Artist", thumbnail, Kind.Artist)
        else -> null
    }

    private fun SongItem.asTrack() = Track(
        id = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        thumbnail = thumbnail,
        durationSeconds = duration,
    )

    /**
     * Seeds for the feed once the catalogue's own shelves run out.
     *
     * The service hands over about eight shelves and then stops, which on a tall
     * window is roughly two scrolls. Rather than end there, each of these
     * becomes a shelf of its own — real results, not filler — so the feed keeps
     * going as long as you keep scrolling.
     */
    private val seeds = listOf(
        "nepali songs", "bollywood hits", "lo-fi beats", "acoustic covers",
        "90s bollywood", "nepali pop", "indie folk", "punjabi hits",
        "romantic hindi songs", "workout songs", "sad songs hindi",
        "chill instrumental", "classic rock", "sufi songs", "party anthems",
        "nepali rock", "arijit singh", "old is gold hindi", "study music",
        "monsoon songs", "road trip songs", "ghazals", "bhajan",
    )

    /** How many shelves the seeds can produce before repeating. */
    val seedCount: Int get() = seeds.size

    /** A shelf built from one seed, by position rather than by name. */
    suspend fun discover(position: Int): Result<Shelf> = withContext(Dispatchers.IO) {
        val seed = seeds[position % seeds.size]
        search(seed).map { tracks ->
            Shelf(
                title = seed.replaceFirstChar { it.uppercase() },
                cards = tracks.take(12).map {
                    Card(it.id, it.title, it.artist, it.thumbnail, Kind.Song)
                },
            )
        }
    }

    /**
     * The songs behind a tile.
     *
     * A song is already what it needs to be; an album or playlist is a browse
     * away. An artist has no single track list worth playing, so it comes back
     * empty and the caller can open the page instead.
     */
    suspend fun open(card: Card): Result<List<Track>> = withContext(Dispatchers.IO) {
        ensureIdentity()
        when (card.kind) {
            Kind.Song -> Result.success(
                listOf(Track(card.id, card.title, card.subtitle, card.thumbnail, null)),
            )
            Kind.Album -> YouTube.album(card.id).map { page -> page.songs.map { it.asTrack() } }
            Kind.Playlist -> YouTube.playlist(card.id).map { page -> page.songs.map { it.asTrack() } }
            Kind.Artist -> Result.success(emptyList())
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
