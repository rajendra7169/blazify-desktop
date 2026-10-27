package com.blazify.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.Cache
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Net
import com.blazify.desktop.data.Listening
import com.blazify.desktop.data.LyricsSource
import com.blazify.desktop.data.Playback
import com.blazify.desktop.data.LastSession
import com.blazify.desktop.data.Resume
import com.blazify.desktop.data.Store
import com.blazify.desktop.data.Scrobbler
import com.blazify.desktop.together.Did
import com.blazify.desktop.together.Together
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Track
import com.blazify.desktop.data.asTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What's playing, and the queue it came from.
 *
 * Sits between the screens and the engine: screens say "play this list from
 * here", and everything about resolving a URL and advancing at the end of a
 * track happens on this side.
 */
object PlayerState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    var failure by mutableStateOf<String?>(null)
        private set

    val current: Track? get() = queue.getOrNull(index)
    val playing: Boolean get() = AudioEngine.playing
    val loading: Boolean get() = AudioEngine.loading || finding

    /**
     * Asking the catalogue where a song's audio is.
     *
     * Nothing has reached the player yet, so the player has nothing to report,
     * but the song is on its way and is shown loading. Left out, a slow answer
     * looked like a song that would never start: 0:00 at both ends and not a
     * word to say anything was happening.
     */
    var finding by mutableStateOf(false)
        private set

    /** Which ask is the current one. An answer to an older one is dropped. */
    private var asking = 0

    /**
     * Longest a song is waited for before saying so.
     *
     * Several sources are asked in turn and each is already held to a few
     * seconds, so this is only reached on a connection that is barely there —
     * where waiting longer would only be waiting.
     */
    private const val FINDING_LIMIT_MS = 45_000L

    /**
     * Where the bar should sit while a seek settles.
     *
     * Asking the engine to move takes a moment — it has to fetch from the new
     * offset — and during that gap it keeps reporting the OLD position. Showing
     * that makes the bar snap backwards the instant you let go, which reads as
     * the seek having failed. Holding the requested position until the engine
     * agrees is what makes scrubbing feel solid.
     */
    private var seekTarget by mutableStateOf<Float?>(null)

    /** 0..1 through the current track, for the progress bar. */
    val progress: Float
        get() = seekTarget
            ?: if (AudioEngine.duration > 0) (AudioEngine.position / AudioEngine.duration).toFloat() else 0f

    /** Where playback actually is, in seconds. What the lyrics follow. */
    val positionSeconds: Double get() = AudioEngine.position

    /**
     * Whether the player is waiting on the stream rather than playing it.
     *
     * Worth surfacing: a long recording takes a moment to gather itself before
     * the first note, and a transport that looks frozen with no explanation is
     * the difference between "it is loading" and "it is broken".
     */
    val buffering: Boolean get() = AudioEngine.buffering || loading

    /** Step forward or back by a few seconds, the way the arrow keys do. */
    fun nudge(seconds: Double) {
        val duration = AudioEngine.duration
        if (duration <= 0) return
        seekTo((AudioEngine.position + seconds).coerceIn(0.0, duration))
    }

    /** Jump to a moment rather than a proportion. */
    fun seekTo(seconds: Double) {
        val duration = AudioEngine.duration
        if (duration <= 0) return
        seek((seconds / duration).toFloat())
    }

    val elapsed: String
        get() = clock(seekTarget?.let { it * AudioEngine.duration } ?: AudioEngine.position)
    val total: String get() = if (AudioEngine.duration > 0) clock(AudioEngine.duration) else current?.duration ?: "0:00"

    var opening by mutableStateOf(false)
        private set

    /** Open a tile and play what's inside it. */
    fun open(card: Catalogue.Card) {
        opening = true
        failure = null
        scope.launch {
            Catalogue.open(card).fold(
                onSuccess = { tracks ->
                    opening = false
                    if (tracks.isEmpty()) failure = "Nothing to play in ${card.title}"
                    else play(tracks)
                },
                onFailure = {
                    opening = false
                    failure = "Couldn't open ${card.title}"
                },
            )
        }
    }

    /**
     * Play a shelf of songs from the one that was clicked.
     *
     * Clicking a song should leave the rest of the shelf lined up behind it —
     * playing it alone and then falling silent is the wrong end of the
     * expectation. Nothing needs fetching: a song card already carries
     * everything a queue entry holds.
     */
    fun playAll(cards: List<Catalogue.Card>, startAt: Int = 0) {
        val songs = cards.filter { it.kind == Catalogue.Kind.Song }
        if (songs.isEmpty()) return
        failure = null
        play(songs.map { it.asTrack() }, startAt)
    }

    /**
     * Play a song and keep going with what it leads to.
     *
     * The song first, then everything the catalogue says is like it — so a
     * radio starts with the thing you asked for rather than with something
     * chosen on its behalf.
     */
    fun startRadio(track: Track) {
        failure = null
        play(listOf(track), 0, "${track.title} radio")
        scope.launch {
            Catalogue.relatedTo(track.id).onSuccess { rest ->
                val more = rest.filterNot { it.id == track.id }
                if (more.isNotEmpty() && current?.id == track.id) queue = queue + more
            }
        }
    }

    /** The same songs in a different order, starting from the top of it. */
    fun shuffle(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        failure = null
        play(tracks.shuffled())
    }

    /**
     * Where the queue came from — an album, a playlist, a radio.
     *
     * Worth keeping because a queue with no name is a list of songs you have to
     * read to recognise. "Playing from Ahista Ahista Mix" is the difference
     * between knowing what you put on and working it out from track four.
     */
    var playingFrom by mutableStateOf<String?>(null)
        private set

    fun play(tracks: List<Track>, startAt: Int = 0) = play(tracks, startAt, null)

    fun play(tracks: List<Track>, startAt: Int, from: String?) {
        playingFrom = from
        queue = tracks
        ordered = tracks
        index = startAt.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        if (shuffling) reshuffle()
        start()
    }

    /**
     * What repeating at the end of the queue does.
     *
     * [One] is about the track, the others about the list — which is why they
     * are one setting rather than two: no one means "repeat this song, and also
     * loop the album afterwards".
     */
    enum class Repeat { Off, All, One }

    var repeat by mutableStateOf(Repeat.Off)
        private set

    fun cycleRepeat() {
        // A count running is the thing the button is showing, so the first
        // press puts that away rather than changing a mode nobody is looking
        // at.
        if (timesLeft > 0) { stopTimes(); return }
        repeat = when (repeat) {
            Repeat.Off -> Repeat.All
            Repeat.All -> Repeat.One
            Repeat.One -> Repeat.Off
        }
    }

    /**
     * How many more times the current song plays before the queue carries on.
     *
     * Different from repeating one song: that is open-ended, and this is
     * "three times, then the rest of the album". It belongs to one song, so it
     * is held with that song's id — and it counts natural endings only.
     * Pressing next or previous is somebody changing their mind, and the
     * count goes with it.
     */
    var timesLeft by mutableStateOf(0)
        private set
    private var timesFor: String? = null

    /** The counts worth offering. */
    val timesChoices = listOf(2, 3, 5, 10)

    /**
     * Play this song [times] times in all, then carry on.
     *
     * The song that is playing counts as the first of them. A song that is not
     * playing is started now — asking for a song three times means wanting to
     * hear it, not wanting it filed somewhere in the queue.
     */
    fun playTimes(track: Track, times: Int) {
        if (times < 2) { stopTimes(); return }
        if (current?.id != track.id) {
            playNext(track)
            next()
        }
        timesFor = track.id
        timesLeft = times - 1
    }

    fun stopTimes() {
        timesLeft = 0
        timesFor = null
    }

    var shuffling by mutableStateOf(false)
        private set

    /** The queue as it was handed over, so shuffling can be undone. */
    private var ordered: List<Track> = emptyList()

    /**
     * Shuffle, or put it back.
     *
     * Turning it on leaves the song that's playing exactly where it is and
     * shuffles what hasn't been reached — jumping to a different track the
     * moment you press shuffle is a jarring thing to do to someone mid-song.
     * Turning it off restores the order it arrived in and finds the current
     * track's place in that.
     */
    fun toggleShuffle() {
        shuffling = !shuffling
        if (queue.isEmpty()) return
        if (shuffling) reshuffle() else {
            val playing = current
            queue = ordered
            playing?.let { index = queue.indexOf(it).coerceAtLeast(0) }
        }
    }

    private fun reshuffle() {
        val playing = current ?: return
        val rest = queue.filterNot { it === playing }.shuffled()
        queue = listOf(playing) + rest
        index = 0
    }

    /**
     * Put a song straight after the one playing.
     *
     * Anything already in the queue is moved rather than duplicated — asking
     * for a song next when it's further down the list means "sooner", not
     * "twice".
     */
    fun playNext(track: Track) {
        if (queue.isEmpty()) { play(listOf(track)); return }
        val without = queue.filterNot { it.id == track.id }
        val at = without.indexOf(current).coerceAtLeast(0)
        queue = without.toMutableList().also { it.add(at + 1, track) }
        index = at
    }

    /** Put a song at the end of the queue. */
    fun addToQueue(track: Track) {
        if (queue.isEmpty()) { play(listOf(track)); return }
        if (queue.any { it.id == track.id }) return
        queue = queue + track
    }

    fun toggle() {
        if (current == null) return
        // Already on its way: a second press would only start the asking over.
        if (finding) return
        // Nothing loaded yet — the first press should start it, not toggle silence.
        if (AudioEngine.duration == 0.0 && !AudioEngine.loading) start() else AudioEngine.toggle()
        // Told after the fact rather than before, so what the room hears is
        // what actually happened here.
        Together.share(if (AudioEngine.playing) Did.PLAY else Did.PAUSE)
    }

    fun next() {
        // A skip is a change of mind, and a count asked for a moment ago is
        // part of the mind that changed.
        stopTimes()
        moveOn()
    }

    /** One step forward, for the queue's own use: a count is left alone. */
    private fun moveOn() {
        if (index + 1 in queue.indices) { index += 1; start() }
    }

    fun previous() {
        stopTimes()
        // Restart the track first, the way every player does, and only step back
        // when you're already near the beginning.
        if (AudioEngine.position > 3.0) AudioEngine.seek(0.0)
        else if (index - 1 in queue.indices) { index -= 1; start() }
    }

    fun seek(fraction: Float) {
        val target = fraction.coerceIn(0f, 1f)
        seekTarget = target
        AudioEngine.seek(target.toDouble())
        Together.share(Did.SEEK)
        scope.launch {
            // Let go once the engine lands near where it was asked to go, or
            // give up after a moment so a failed seek can't freeze the bar.
            val deadline = System.currentTimeMillis() + 2000
            while (System.currentTimeMillis() < deadline) {
                delay(60)
                val duration = AudioEngine.duration
                if (duration <= 0) continue
                val now = (AudioEngine.position / duration).toFloat()
                if (kotlin.math.abs(now - target) < 0.01f) break
            }
            seekTarget = null
        }
    }

    /** Jump straight to a track in the queue. */
    fun jumpTo(position: Int) {
        if (position !in queue.indices || position == index) return
        index = position
        start()
    }

    /** Drop a track. Removing the one playing moves on to the next. */
    fun removeAt(position: Int) {
        if (position !in queue.indices) return
        val wasCurrent = position == index
        queue = queue.toMutableList().also { it.removeAt(position) }
        when {
            queue.isEmpty() -> { AudioEngine.stop(); index = 0 }
            wasCurrent -> { index = index.coerceAtMost(queue.lastIndex); start() }
            position < index -> index -= 1
        }
    }

    /** Reorder by dragging, keeping the playing track under the same finger. */
    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val playing = current
        queue = queue.toMutableList().also { it.add(to, it.removeAt(from)) }
        playing?.let { index = queue.indexOf(it).coerceAtLeast(0) }
    }

    /**
     * How fast talk plays.
     *
     * Only talk. Speeding up a song changes the pitch of it and the whole
     * point of the song with it, but an hour of somebody explaining something
     * at one and a half times is the same hour with forty minutes given back —
     * which is why every application that carries speech has this and no
     * application that only carries music does.
     *
     * Remembered, since somebody who listens at one and a quarter listens at
     * one and a quarter to everything, and being asked again each episode is
     * being asked the same question forever.
     */
    var speed by mutableStateOf(
        runCatching { java.io.File(Store.folder, "speed").readText().trim().toFloat() }
            .getOrDefault(1f),
    )
        private set

    fun chooseSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 3f)
        runCatching { java.io.File(Store.folder, "speed").writeText(speed.toString()) }
        // Only where it belongs. A song is left alone whatever this says.
        if (current?.talk == true) AudioEngine.setRate(speed)
    }

    /** The speeds worth offering, which is not every number between them. */
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    var volume by mutableStateOf(0.8f)
        private set

    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        if (volume > 0f) beforeMute = volume
        AudioEngine.setVolume(volume.toDouble())
    }

    /** What to go back to when unmuting. */
    private var beforeMute = 0.8f

    fun toggleMute() {
        changeVolume(if (volume > 0f) 0f else beforeMute)
    }

    init {
        // Nothing else is watching the engine, so the queue would stall on the
        // first track without this.
        AudioEngine.onFinished = { advance() }

        // A link found while the connection was still there, handed to the
        // player after it went: the player gives up, and nothing else would
        // notice. Seen here, and treated like any song that can't be fetched.
        scope.launch {
            var handled: String? = null
            while (true) {
                delay(500)
                val track = current ?: continue
                if (AudioEngine.error == null) { handled = null; continue }
                if (handled == track.id || playableOffline(track)) continue
                handled = track.id
                if (!Net.checkNow() && current?.id == track.id) continueOffline()
            }
        }

        // Where you had got to, noted as you go. Not only while playing: a
        // pause is exactly when somebody is about to walk away, and the
        // position at that moment is the one worth having.
        scope.launch {
            while (true) {
                delay(3000)
                val track = current ?: continue
                val length = AudioEngine.duration
                if (length > 0 && resuming == null) {
                    Resume.note(track, AudioEngine.position, length)
                }
                // The whole queue too, not just this recording — on the same
                // tick, so a crash costs three seconds rather than the evening.
                remember()
            }
        }
    }

    /**
     * The link for the next track, found before it is wanted.
     *
     * Kept here rather than handed to the player. There is only one player and
     * giving it the next track is giving up the current one — which is exactly
     * what happened: four seconds into every streamed song the audio stopped
     * dead, the bar froze and the button went back to play, while anything on
     * disk carried on fine because it skipped this path entirely.
     *
     * Resolving is the slow half anyway. The gap between two songs is mostly a
     * conversation with the catalogue — which client to claim to be, which
     * link comes back, whether it is refused — and having that answer in hand
     * before the last note is what removes it.
     */
    private var readyNext: Pair<String, Catalogue.Stream>? = null

    private fun warmNext() {
        val next = queue.getOrNull(index + 1) ?: return
        if (next.stream != null) return
        if (LocalMusic.isLocal(next.id) || Downloads.has(next.id) || Cache.has(next.id)) return
        if (readyNext?.first == next.id) return
        scope.launch {
            // A moment in, so it never competes with the track that is
            // actually starting for the same connection.
            delay(4000)
            if (queue.getOrNull(index + 1)?.id != next.id) return@launch
            Catalogue.stream(next.id).getOrNull()?.let { readyNext = next.id to it }
        }
    }

    /**
     * Put the chosen speed back on every new piece of audio.
     *
     * The engine forgets it each time — it is a property of what is playing
     * rather than of the player — so it is set again here. Talk and songs each
     * have their own number; a song's is one unless somebody chose otherwise.
     */
    private fun applySpeed(track: Track) {
        AudioEngine.setRate(if (track.talk) speed else Listening.speed)
    }

    /**
     * How fast songs play, chosen apart from talk.
     *
     * The two are separate numbers because they are separate wishes: an
     * episode at one and a half and a playlist at its own pace is the
     * ordinary case, and one number for both would get one of them wrong.
     * The key is kept unless asked otherwise — see [Listening.naturalPitch].
     */
    fun chooseMusicSpeed(value: Float) {
        Listening.chooseSpeed(value)
        if (current?.talk == false) AudioEngine.setRate(Listening.speed)
    }

    /**
     * Where a resume is heading, while it gets there.
     *
     * A track opens at nought and stays there for a moment before the seek
     * lands. Recording that would erase the mark being resumed to, one tick
     * before it was used — so nothing is written while a jump is in flight.
     */
    private var resuming: Double? = null

    /**
     * Pick up where this was left off, if it was left off anywhere.
     *
     * Only long things carry a mark at all — see [Resume] — so this never
     * drops somebody two thirds of the way through a song they wanted to hear.
     * The seek waits for the stream to say how long it is, because a jump
     * asked for before that is a jump into nothing.
     */
    private fun resumeIfLeftOff(track: Track) {
        // A restored session takes precedence: it is where this very song was
        // when the window closed, which is newer than any mark left behind.
        val seconds = restoredAt?.takeIf { track.id == restoredFor } ?: Resume.mark(track.id)?.seconds
        restoredAt = null
        restoredFor = null
        if (seconds == null || seconds <= 1.0) return
        resumeAt(track, seconds)
    }

    private fun resumeAt(track: Track, seconds: Double) {
        resuming = seconds
        scope.launch {
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline) {
                delay(100)
                // Skipped past already: the jump belongs to a track nobody is
                // listening to any more.
                if (current?.id != track.id) break
                if (AudioEngine.duration <= 0) continue
                seekTo(seconds)
                delay(600)
                break
            }
            resuming = null
        }
    }

    /** Where the restored song was, and which song that was. */
    private var restoredAt: Double? = null
    private var restoredFor: String? = null

    /**
     * Put back the queue the window closed on, paused where it stopped.
     *
     * Nothing plays. The transport fills in so the bar and the times are not
     * blank before the first press, and the first press starts the song from
     * where it was rather than from the top.
     */
    fun restore() {
        if (queue.isNotEmpty()) return
        val last = LastSession.load() ?: return
        queue = last.tracks
        ordered = last.tracks
        index = last.index
        restoredAt = last.seconds
        restoredFor = last.tracks[last.index].id
    }

    /**
     * Keep the queue and the position, for the next time the window opens.
     *
     * Called as songs change and while one plays, so a crash or a power cut
     * loses a few seconds rather than the evening.
     */
    fun remember() {
        LastSession.save(queue, index, restoredAt ?: AudioEngine.position)
    }

    /**
     * Bring a song in rather than have it arrive.
     *
     * A short ramp on the way in, which is enough to stop a track landing like
     * a slammed door — and a longer one when a fade has been asked for. It sets
     * the volume back to where it was, never above it: the number on the slider
     * is the number that plays.
     */
    private fun fadeUp() {
        val seconds = when {
            Playback.fadeSeconds > 0f -> Playback.fadeSeconds
            Playback.easeIn -> 0.35f
            else -> return
        }
        val target = volume
        scope.launch {
            val steps = (seconds * 20).toInt().coerceIn(4, 160)
            AudioEngine.setVolume(0.0)
            repeat(steps) { step ->
                delay((seconds * 1000 / steps).toLong())
                AudioEngine.setVolume(target.toDouble() * (step + 1) / steps)
            }
            AudioEngine.setVolume(target.toDouble())
        }
    }

    /**
     * Open the stream again and carry on where it stopped.
     *
     * The catalogue's stream links refuse a second connection — the first one
     * works and every reconnection after it is turned away. A short song
     * finishes inside that one connection and nobody notices; a long one does
     * not, and stops in the middle. So when the audio goes quiet, a fresh link
     * is fetched and playback resumes at the same second.
     *
     * Guarded, because a link that keeps failing must not become a loop of
     * requests: three goes at one song, then it is left alone.
     */
    private var reopening = false
    private var reopenedAt = 0
    private var reopenedFor: String? = null

    /**
     * Fetch the stream again and carry on.
     *
     * Called only when the player reports a hard failure — never on a guess
     * that something has gone quiet. Guessing cost two evenings: a song that
     * was merely slow to start got restarted, which made it slower to start,
     * which looked exactly like nothing playing at all.
     */
    fun recover() = reopen()

    private fun reopen() {
        val track = current ?: return
        if (reopening) return
        if (reopenedFor != track.id) {
            reopenedFor = track.id
            reopenedAt = 0
        }
        if (reopenedAt >= 3) return

        // Nothing to carry on from means nothing has played yet, and a song
        // that has not started is not a song that has stalled.
        val resumeFrom = AudioEngine.position
        if (resumeFrom < 1.0) return

        reopening = true
        reopenedAt += 1

        // Found already, while the last song was playing. A link is only good
        // once, so it is taken rather than copied — a second play of the same
        // song asks again.
        readyNext?.takeIf { it.first == track.id }?.let { (_, ready) ->
            readyNext = null
            AudioEngine.play(ready.url, ready.userAgent)
            applySpeed(track)
            fadeUp()
            resumeIfLeftOff(track)
            Library.played(track)
            warmNext()
            return
        }

        scope.launch {
            Catalogue.stream(track.id).fold(
                onSuccess = { AudioEngine.play(it.url, resumeFrom, it.userAgent) },
                onFailure = { why ->
                    val refused = why is Catalogue.BotCheck
                    if (!refused && current?.id == track.id && !Net.checkNow() && continueOffline()) {
                        reopening = false
                        return@launch
                    }
                    failure = if (refused) refusalMessage() else "Lost the connection to ${track.title}"
                    if (!refused && Playback.skipBroken && index + 1 in queue.indices) next()
                },
            )
            reopening = false
        }
    }

    /** True while the queue is being lengthened, so it is only done once. */
    private var extending = false

    /**
     * Carry on past the end of the queue.
     *
     * Built from the last thing played rather than the first, because what you
     * want next follows from what you just heard — an hour into a queue, the
     * song it started with is not the thread any more.
     */
    private fun extend() {
        if (extending) return
        val seed = queue.lastOrNull() ?: return
        extending = true
        scope.launch {
            Catalogue.relatedTo(seed.id).onSuccess { more ->
                val fresh = more.filterNot { song -> queue.any { it.id == song.id } }
                if (fresh.isNotEmpty()) {
                    queue = queue + fresh
                    ordered = queue
                    index += 1
                    start()
                }
            }
            extending = false
        }
    }

    /**
     * What happens when a track runs out on its own.
     *
     * Distinct from pressing next: reaching the end of a song is when repeat
     * gets a say, and pressing skip on the last track should still stop rather
     * than silently start the album again.
     */
    private fun advance() {
        // Asked before anything else: a timer set to end here means the queue
        // stops, whatever repeat would otherwise have done.
        if (SleepTimer.consumeTrackEnd()) return
        // A count asked for comes before any mode: it is the more specific
        // wish, and it ends on its own.
        if (timesLeft > 0 && current?.id == timesFor) {
            timesLeft -= 1
            if (timesLeft == 0) timesFor = null
            start()
            return
        }
        when {
            repeat == Repeat.One -> start()
            index + 1 in queue.indices -> moveOn()
            repeat == Repeat.All && queue.isNotEmpty() -> { index = 0; start() }

            // Nothing left and nothing repeating. Rather than stop dead, keep
            // going from where the queue ended up — silence at the end of an
            // album is a decision the album made, not one you made.
            Playback.keepGoing && queue.isNotEmpty() -> extend()
        }
    }

    /**
     * Treat the song as over, though the player has not reached its end.
     *
     * For a song whose last stretch is being passed over: the same as running
     * out on its own, so a repeat, a count or a timer set for the end of the
     * song each still get their say.
     */
    fun endEarly() {
        if (SleepTimer.consumeTrackEnd()) {
            if (AudioEngine.playing) AudioEngine.toggle()
            return
        }
        advance()
    }

    private fun start() {
        val track = current ?: return
        // Whatever was being found for the last song is no longer wanted.
        val ticket = ++asking
        finding = false
        // Whatever moved the queue to another song took the count with it.
        if (timesFor != null && timesFor != track.id) stopTimes()
        // Told first, so the asking for what to skip overlaps the asking for
        // the audio rather than following it.
        Skipper.began(track)
        Ahead.began()
        reopenedFor = track.id
        reopenedAt = 0
        // Everyone in the room hears what the host started, and hears it from
        // their own copy — the wire carries which song and where in it, never
        // the audio.
        Together.share(Did.CHANGE_TRACK)
        failure = null
        seekTarget = null

        // The next track's link is found while this one plays. The gap between
        // two songs is mostly the conversation with the catalogue — which
        // client to claim to be, which link comes back, whether it is refused —
        // and having that answer in hand before the last note removes it.
        // The link only; handing the player the next track is giving up the
        // one it is playing.
        warmNext()

        // The words are fetched now rather than when the panel is opened, and
        // the next song's are fetched with them. Several services asked over
        // the network take a few seconds however well it's done — the fix is
        // for those seconds to happen while the song is starting instead of
        // while somebody is staring at an empty panel.
        LyricsSource.warm(track)
        // The next two, not just the next one. Skipping twice in a row is
        // ordinary, and the point of fetching ahead is that the panel is never
        // the thing being waited for.
        LyricsSource.warm(queue.getOrNull(index + 1))
        LyricsSource.warm(queue.getOrNull(index + 2))
        Scrobbler.began(track)
        if (!AudioEngine.available()) {
            failure = "Audio support is missing — install VLC and restart Blazify"
            return
        }
        // Some things arrive knowing where their audio is. A feed says so
        // outright, and there is nothing to negotiate — no client to pretend
        // to be, no link bound to whoever asked for it. Checked after the
        // kept copy and before the catalogue, because a copy on disk still
        // beats an ordinary file on somebody else's server.
        val known = track.stream

        // Anything already on disk needs no resolving — hand the path straight
        // over. A kept copy is preferred to the network even when there is one:
        // it starts sooner and can't stall halfway through.
        val onDisk = when {
            LocalMusic.isLocal(track.id) -> LocalMusic.pathOf(track.id)
            Downloads.has(track.id) -> Downloads.fileFor(track.id).absolutePath
            // Kept quietly the last time this played. Same file, same instant
            // start, and the only difference from a download is that nobody
            // asked for it.
            Cache.has(track.id) -> Cache.fileFor(track.id).absolutePath
            else -> null
        }
        if (onDisk != null) {
            offlineHops = 0
            AudioEngine.play(onDisk)
            applySpeed(track)
            fadeUp()
            resumeIfLeftOff(track)
            Library.played(track)
            return
        }

        if (known != null) {
            AudioEngine.play(known)
            applySpeed(track)
            fadeUp()
            resumeIfLeftOff(track)
            Library.played(track)
            return
        }

        // Known to be offline, and this one is only out there. Asking the
        // catalogue would only fail after a wait, so go straight to the next
        // song that is here.
        // Only when there is somewhere to go: the flag can be a few seconds
        // behind a connection that just came back, and then asking is right.
        if (!Net.online && continueOffline(stopIfNone = false)) return

        // Found already, while the last song was playing. A link is only good
        // once, so it is taken rather than copied — a second play of the same
        // song asks again.
        readyNext?.takeIf { it.first == track.id }?.let { (_, ready) ->
            readyNext = null
            AudioEngine.play(ready.url, ready.userAgent)
            applySpeed(track)
            fadeUp()
            resumeIfLeftOff(track)
            Library.played(track)
            warmNext()
            return
        }

        finding = true
        scope.launch {
            val found = withTimeoutOrNull(FINDING_LIMIT_MS) { Catalogue.stream(track.id) }
                ?: Result.failure(Catalogue.TooSlow())
            // Moved on while this was being asked: playing it now would take
            // over from the song that was chosen since.
            if (ticket != asking) return@launch
            finding = false
            found.fold(
                onSuccess = {
                    offlineHops = 0
                    AudioEngine.play(it.url, it.userAgent)
                    applySpeed(track)
                    fadeUp()
                    resumeIfLeftOff(track)
                    // Recorded once it's actually playing rather than on the
                    // click, so a track that never resolves doesn't leave a
                    // false entry in a list of what you listened to.
                    Library.played(track)
                },
                onFailure = { why ->
                    // A song YouTube will not hand over without an account is
                    // not a broken song, and the next one will be refused for
                    // the same reason — skipping there only races through the
                    // queue, which is what "it just goes next, next, next"
                    // looks like from the outside.
                    val refused = why is Catalogue.BotCheck
                    // No connection at all is not a broken song. Carry on
                    // with what is on this computer instead of stopping.
                    if (!refused && current?.id == track.id && !Net.checkNow() && continueOffline()) {
                        return@fold
                    }
                    failure = when {
                        refused -> refusalMessage()
                        why is Catalogue.TooSlow ->
                            "${track.title} took too long to arrive — the connection may be weak. Press play to try again"
                        else -> "Couldn't play ${track.title}"
                    }
                    // Sitting on a song that will not play, waiting to be
                    // rescued, is the worst of the options — so move on unless
                    // that was asked for.
                    if (!refused && why !is Catalogue.TooSlow && Playback.skipBroken && index + 1 in queue.indices) next()
                },
            )
        }
    }

    /**
     * How many songs in a row have been passed over for want of a connection.
     *
     * The guard against going round forever: each pass moves forward to a
     * song that is on this computer, and anything that actually plays puts it
     * back to nought. Once it has covered the whole queue without finding one,
     * it stops and says so.
     */
    private var offlineHops = 0

    /** Whether a song can play with no connection at all. */
    private fun playableOffline(track: Track): Boolean =
        LocalMusic.isLocal(track.id) || Downloads.has(track.id) || Cache.has(track.id)

    /**
     * Move to the next song in the queue that is here, rather than stop.
     *
     * Forward only, and round to the top only when the queue repeats — the
     * same rule the end of a song follows. False when there is nothing to move
     * to, so the ordinary failure can be shown instead. When the connection
     * comes back nothing needs undoing: the next song streams as usual.
     */
    private fun continueOffline(stopIfNone: Boolean = true): Boolean {
        if (queue.isEmpty() || offlineHops >= queue.size) return false
        val order = buildList {
            addAll(index + 1 until queue.size)
            if (repeat == Repeat.All) addAll(0 until index)
        }
        val found = order.firstOrNull { playableOffline(queue[it]) }
        if (found == null) {
            if (!stopIfNone) return false
            failure = "No connection, and nothing further in the queue is on this computer"
            AudioEngine.stop()
            return true
        }
        offlineHops += found.let { if (it > index) it - index else queue.size - index + it }
        index = found
        start()
        // Said after starting, which clears the last message, so this one stays.
        failure = "No connection — playing what's on this computer"
        return true
    }

    /**
     * What to say when YouTube refused every way of asking.
     *
     * Not "couldn't play this song": the song is fine and the next one will be
     * refused in the same breath, so saying so is the difference between a
     * player that looks broken and one that tells you what happened.
     *
     * It does not suggest signing in, which it used to. Asked signed in and
     * signed out, for the same song, the service answers identically — the same
     * scrambled offer either way. The check is about the program, not the
     * person, so telling somebody to sign in would be sending them to do
     * something that measurably changes nothing.
     */
    private fun refusalMessage(): String =
        "YouTube wanted proof this isn't a robot, and no source could give it — " +
            "Settings shows what each of them said"

    /** Put the last failure away, once it has been read. */
    fun forgetFailure() { failure = null }

    /** Keep whatever is playing, so it works without the network. */
    fun downloadCurrent() {
        current?.let { Downloads.start(it) }
    }

    /** Like or unlike whatever is playing. */
    fun toggleLike() {
        current?.let { Library.toggleLike(it) }
    }

    val currentLiked: Boolean get() = current?.let { Library.isLiked(it.id) } == true

    private fun clock(seconds: Double): String {
        val whole = seconds.toInt().coerceAtLeast(0)
        return "%d:%02d".format(whole / 60, whole % 60)
    }
}
