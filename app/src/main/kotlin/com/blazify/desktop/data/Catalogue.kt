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

/** A song card is already a track — nothing needs fetching before it plays. */
fun Catalogue.Card.asTrack() = Track(
    id = id,
    title = title,
    artist = subtitle,
    thumbnail = thumbnail,
    durationSeconds = durationSeconds,
)

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
        val durationSeconds: Int? = null,
    ) {
        val duration: String
            get() = durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: ""

        /**
         * Whether the artwork is a widescreen still rather than a cover.
         *
         * Live sets, music videos and anything lifted from a video carry a 16:9
         * frame, and cropping one to a square throws away the half of the shot
         * that made it worth looking at. Nothing says so outright, but the size
         * baked into the URL does — and a video still comes from a different
         * host altogether.
         */
        val wide: Boolean
            get() {
                val url = thumbnail ?: return false
                if ("ytimg.com" in url) return true
                val (w, h) = Sized.find(url)?.destructured ?: return false
                val width = w.toIntOrNull() ?: return false
                val height = h.toIntOrNull() ?: return false
                return width * 5 > height * 6
            }
    }

    private val Sized = Regex("=w(\\d+)-h(\\d+)")

    /**
     * One shelf of the feed: a heading and the tiles under it.
     *
     * Some shelves arrive with more than a title — a line of context above it
     * ("Similar to", "Listen again") and a small picture of whoever it came
     * from. Those turn a wall of equal headings into something you can skim,
     * so they're carried through rather than dropped.
     */
    data class Shelf(
        val title: String,
        val cards: List<Card>,
        val label: String? = null,
        val avatar: String? = null,
        /**
         * How deep the shelf stacks: one means a row of artwork, more means a
         * grid of compact lines. The catalogue decides this per shelf — the
         * same songs are worth four rows in one place and full-size cards in
         * another — so it's taken rather than guessed.
         */
        val rows: Int = 1,
        /** Whether the tiles were asked to be shown large. */
        val big: Boolean = false,
    ) {
        /** A grid of lines only makes sense for things you play directly. */
        val isSongs: Boolean
            get() = rows > 1 && cards.isNotEmpty() && cards.all { it.kind == Kind.Song }
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
                    if (cards.isEmpty()) null else Shelf(
                        title = section.title,
                        cards = cards,
                        label = section.label,
                        avatar = section.thumbnail,
                        rows = section.rows,
                        big = section.size?.contains("LARGE") == true,
                    )
                },
                more = page.continuation,
            )
        }
    }

    private fun com.blazify.innertube.models.YTItem.asCard(): Card? = when (this) {
        is SongItem -> Card(id, title, artists.joinToString(", ") { it.name }, thumbnail, Kind.Song, duration)
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
                    Card(it.id, it.title, it.artist, it.thumbnail, Kind.Song, it.durationSeconds)
                },
                rows = 4,
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
            Kind.Song -> Result.success(listOf(card.asTrack()))
            Kind.Album -> YouTube.album(card.id).map { page -> page.songs.map { it.asTrack() } }
            Kind.Playlist -> YouTube.playlist(card.id).map { page -> page.songs.map { it.asTrack() } }
            Kind.Artist -> Result.success(emptyList())
        }
    }

    /**
     * A page for one thing: what it is, and what's inside it.
     *
     * An album or a playlist is a track list. An artist isn't — there's no one
     * list worth playing, so it comes back as shelves instead, the same shape
     * the feed is built from, and the page draws whichever it was given.
     */
    data class Collection(
        val card: Card,
        val tracks: List<Track> = emptyList(),
        val shelves: List<Shelf> = emptyList(),
        val note: String? = null,
    )

    suspend fun collection(card: Card): Result<Collection> = withContext(Dispatchers.IO) {
        ensureIdentity()
        when (card.kind) {
            Kind.Song -> Result.success(Collection(card, tracks = listOf(card.asTrack())))

            Kind.Album -> YouTube.album(card.id).map { page ->
                Collection(
                    card = card.copy(thumbnail = page.album.thumbnail ?: card.thumbnail),
                    tracks = page.songs.map { it.asTrack() },
                    note = page.album.year?.toString(),
                )
            }

            Kind.Playlist -> YouTube.playlist(card.id).map { page ->
                Collection(
                    card = card.copy(thumbnail = page.playlist.thumbnail ?: card.thumbnail),
                    tracks = page.songs.map { it.asTrack() },
                    note = page.playlist.songCountText,
                )
            }

            Kind.Artist -> YouTube.artist(card.id).map { page ->
                Collection(
                    card = card.copy(thumbnail = page.artist.thumbnail ?: card.thumbnail),
                    // The top shelf of an artist page is their songs, which is
                    // what the play button on the header should reach for.
                    tracks = page.sections.firstOrNull()
                        ?.items?.filterIsInstance<SongItem>()?.map { it.asTrack() }
                        .orEmpty(),
                    shelves = page.sections.mapNotNull { section ->
                        val cards = section.items.mapNotNull { it.asCard() }
                        if (cards.isEmpty()) null
                        else Shelf(section.title, cards, rows = if (cards.all { it.kind == Kind.Song }) 4 else 1)
                    },
                    note = page.subscriberCountText,
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
