package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Normalizer

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Turning somebody's list from elsewhere into a playlist here.
 *
 * Whatever the list came from — a file, another service, a link a friend sent —
 * it arrives as lines that each want one song. A line that already names the
 * song by its id is fetched as it is; everything else is searched for by its
 * title and artist, and the closest song in the answer is taken. A line with
 * no close answer is left out rather than filled with a guess, and the count
 * at the end says so.
 *
 * One import at a time. Two running at once would race for the same progress
 * line and neither would be readable.
 */
object PlaylistImport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** How far an import has got, while one is running. */
    data class Progress(val name: String, val done: Int, val total: Int, val found: Int)

    /** How one finished. */
    data class Outcome(
        val name: String,
        val found: Int,
        val total: Int,
        /** The playlist made, or null when nothing at all was found. */
        val playlistId: String?,
        /** The lines nothing was found for, to show by name. */
        val missing: List<Wanted>,
        /** Anything worth saying about where the list came from. */
        val note: String? = null,
    ) {
        val summary: String
            get() = if (found == 0) "None of the $total songs could be found"
            else "$found of $total songs found"
    }

    var progress by mutableStateOf<Progress?>(null)
        private set

    var outcome by mutableStateOf<Outcome?>(null)
        private set

    /** Said while the list itself is still being fetched, before there is a count. */
    var reading by mutableStateOf<String?>(null)
        private set

    /** Why the list itself could not be had, which is different from songs not found. */
    var problem by mutableStateOf<String?>(null)
        private set

    val busy: Boolean get() = job?.isActive == true

    /** At most this many searches out at once — quick, without hammering anybody. */
    private const val AT_ONCE = 4

    /**
     * Fetch a list first, then import it.
     *
     * For lists that live somewhere else and have to be asked for. [what]
     * names the place, so the wait says what it's waiting on.
     */
    fun fetchThen(what: String, fetch: suspend () -> Result<Pair<String, List<Wanted>>>, note: ((List<Wanted>) -> String?)? = null) {
        if (busy) return
        outcome = null
        problem = null
        reading = "Reading the list from $what…"
        job = scope.launch {
            fetch().fold(
                onSuccess = { (name, wanted) ->
                    reading = null
                    run(name, wanted, note?.invoke(wanted))
                },
                onFailure = {
                    reading = null
                    problem = it.message ?: "Couldn't read that list"
                },
            )
        }
    }

    /** Import a list already in hand. */
    fun start(name: String, wanted: List<Wanted>, note: String? = null) {
        if (busy) return
        outcome = null
        problem = null
        reading = null
        job = scope.launch { run(name, wanted, note) }
    }

    fun cancel() {
        job?.cancel()
        job = null
        progress = null
        reading = null
    }

    fun dismiss() {
        outcome = null
        problem = null
    }

    private suspend fun run(name: String, wanted: List<Wanted>, note: String?) {
        if (wanted.isEmpty()) {
            problem = "There are no songs in that list"
            return
        }
        val total = wanted.size
        val results = arrayOfNulls<Track>(total)
        var done = 0
        var found = 0
        progress = Progress(name, 0, total, 0)

        fun tick(hit: Boolean) {
            done += 1
            if (hit) found += 1
            progress = Progress(name, done, total, found)
        }

        // Files on this machine first: no network, and no doubt about which song.
        wanted.forEachIndexed { at, line ->
            val path = line.path ?: return@forEachIndexed
            val id = LocalMusic.PREFIX + path
            results[at] = LocalMusic.tracks.firstOrNull { it.id == id }
                ?: Track(id = id, title = line.title, artist = line.artist, thumbnail = null, durationSeconds = line.seconds)
            tick(true)
        }

        // Then everything that already named its song, in one go.
        val byId = wanted.withIndex().filter { results[it.index] == null && it.value.id != null }
        if (byId.isNotEmpty()) {
            val songs = Catalogue.songs(byId.map { it.value.id!! }).getOrDefault(emptyList()).associateBy { it.id }
            byId.forEach { (at, line) ->
                val song = songs[line.id]
                results[at] = song
                // An id the catalogue no longer knows still has a name, if the
                // file gave one, and is searched for like any other line.
                if (song != null || line.title.isBlank()) tick(song != null)
            }
        }

        // And the rest by searching, a few at a time.
        val limit = Semaphore(AT_ONCE)
        coroutineScope {
            wanted.withIndex()
                .filter { results[it.index] == null && it.value.title.isNotBlank() }
                .map { (at, line) ->
                    async {
                        limit.withPermit {
                            val query = listOf(line.title, firstArtist(line.artist)).filter { it.isNotBlank() }.joinToString(" ")
                            val match = Catalogue.search(query).getOrNull()?.let { Matching.best(line, it) }
                            results[at] = match
                            synchronized(this@PlaylistImport) { tick(match != null) }
                        }
                    }
                }
                .awaitAll()
        }

        val tracks = results.filterNotNull().distinctBy { it.id }
        val made = if (tracks.isNotEmpty()) Playlists.create(name, tracks) else null
        progress = null
        outcome = Outcome(
            name = made?.name ?: name,
            found = results.count { it != null },
            total = total,
            playlistId = made?.id,
            missing = wanted.filterIndexed { at, _ -> results[at] == null },
            note = note,
        )
    }

    private fun firstArtist(artists: String) = artists.split(",", ";", " & ", " feat. ", " ft. ").first().trim()
}

/**
 * Which of a search's answers is the song a line asked for.
 *
 * A search for "title artist" usually puts the right song first, but not
 * always — a cover, a live take or a karaoke version can outrank it. Each
 * answer is scored on how much of the asked-for title it carries, whether the
 * artist matches, and how close its length is when both lengths are known.
 * Nothing that shares less than half the title is taken: a wrong song in a
 * playlist is worse than a missing one, because nobody notices it until it
 * plays.
 */
object Matching {

    fun best(wanted: Wanted, found: List<Track>): Track? {
        val title = words(clean(wanted.title))
        if (title.isEmpty()) return null
        val artist = words(wanted.artist)

        return found.withIndex()
            .filterNot { it.value.talk }
            .mapNotNull { (rank, track) ->
                val theirs = words(clean(track.title))
                val titleScore = coverage(title, theirs).coerceAtMost(coverage(theirs, title) + 0.5)
                if (titleScore < 0.5) return@mapNotNull null

                val artistScore = if (artist.isEmpty()) 0.5 else coverage(artist, words(track.artist))
                val lengthScore = when {
                    wanted.seconds == null || track.durationSeconds == null -> 0.5
                    else -> {
                        val off = kotlin.math.abs(wanted.seconds - track.durationSeconds)
                        when {
                            off <= 3 -> 1.0
                            off <= 10 -> 0.6
                            off <= 30 -> 0.25
                            else -> 0.0
                        }
                    }
                }
                // Versions nobody asked for, unless the title asked for them.
                val unwanted = UNWANTED.count { it in theirs && it !in title } * 0.2
                // The catalogue's own order counts for a little, as a tiebreak.
                val order = (10 - rank).coerceAtLeast(0) * 0.005

                track to (titleScore * 0.55 + artistScore * 0.3 + lengthScore * 0.15 - unwanted + order)
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private val UNWANTED = setOf("karaoke", "instrumental", "cover", "remix", "live", "sped", "slowed", "nightcore")

    /** How much of [part] is found in [whole], from 0 to 1. */
    private fun coverage(part: Set<String>, whole: Set<String>): Double =
        if (part.isEmpty()) 0.0 else part.count { it in whole }.toDouble() / part.size

    /**
     * A title without the parts that differ between services for the same
     * song: "(feat. somebody)", "- 2011 Remaster", "[Official Video]".
     */
    internal fun clean(title: String): String = title
        .replace(Regex("\\((?:feat|ft|with)\\.?[^)]*\\)", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("\\s[-–]\\s.*(?:remaster|version|edit|mix|mono|stereo).*$", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\((?:[^)]*remaster[^)]*|official[^)]*|lyrics?[^)]*)\\)", RegexOption.IGNORE_CASE), " ")

    internal fun words(text: String): Set<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('&', ' ')
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() && it !in FILLER }
            .toSet()

    private val FILLER = setOf("the", "a", "an", "and", "feat", "ft", "with", "x")
}
