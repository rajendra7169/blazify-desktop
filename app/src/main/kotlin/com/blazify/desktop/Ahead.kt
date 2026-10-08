package com.blazify.desktop

import com.blazify.desktop.audio.AudioEngine
import com.blazify.desktop.data.Cache
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Metered
import com.blazify.desktop.data.Net
import com.blazify.desktop.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Keeps the next few songs on disk before they are reached.
 *
 * Fetching only the next song's link means a dropped connection stops the
 * music at the end of the song that is playing. With the next three already
 * kept, a wifi that drops for ten minutes is ten minutes nobody notices.
 *
 * One worker, one song at a time, one piece at a time — and it gives way. It
 * waits until the song that is playing has settled (on disk already, or
 * fifteen seconds in and not short of audio), and before every piece it waits
 * again if the player has started struggling. The song that is playing must
 * never be the one that pays for the song after next.
 *
 * Only on a connection that isn't counted by the byte; see [Metered].
 */
object Ahead {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** How many songs past the current one are kept ready. */
    private const val HOW_MANY = 3

    /** When the current song began, to know when it has settled. */
    @Volatile private var startedAt = System.currentTimeMillis()

    /** Songs that wouldn't come, and when — not asked for again for a while. */
    private val refused = mutableMapOf<String, Long>()

    init {
        scope.launch {
            while (isActive) {
                delay(3000)
                runCatching { once() }
            }
        }
    }

    /** A new song has started; the waiting starts over. */
    fun began() {
        startedAt = System.currentTimeMillis()
    }

    private suspend fun once() {
        if (!Cache.on || !Cache.aheadOn) return
        if (!Net.online || !settled()) return
        val wanted = upcoming().firstOrNull() ?: return
        // Asked only once there is something to fetch, since on Windows the
        // asking itself starts a small program.
        if (!Metered.free()) return

        val kept = Cache.ahead(wanted) {
            // Before each piece: the playing song first, always.
            while (!settled() || !Net.online) delay(1000)
        }
        if (!kept && !Cache.has(wanted.id)) {
            synchronized(refused) { refused[wanted.id] = System.currentTimeMillis() }
        }
    }

    /** The next few songs that would need the network, nearest first. */
    private fun upcoming(): List<Track> {
        val queue = PlayerState.queue
        val from = PlayerState.index + 1
        val now = System.currentTimeMillis()
        return (from until (from + HOW_MANY).coerceAtMost(queue.size))
            .map { queue[it] }
            .filter { track ->
                // A feed's episode is long talk at a known address — kept by
                // downloading it, not by guessing it will be wanted.
                !track.talk && track.stream == null &&
                    !LocalMusic.isLocal(track.id) && !Downloads.has(track.id) &&
                    !Cache.has(track.id) && !Cache.isFetching(track.id) &&
                    synchronized(refused) { refused[track.id] }?.let { now - it > 10 * 60_000 } != false
            }
    }

    /**
     * Whether the song that is playing has all it needs.
     *
     * Paused counts as settled — nothing is competing for the connection then.
     * A song playing from disk is settled at once; one arriving over the
     * network after fifteen seconds without running short.
     */
    private fun settled(): Boolean {
        val track = PlayerState.current ?: return true
        if (AudioEngine.loading || AudioEngine.buffering || AudioEngine.stalled) return false
        if (!AudioEngine.playing) return AudioEngine.duration > 0
        val onDisk = LocalMusic.isLocal(track.id) || Downloads.has(track.id) || Cache.has(track.id)
        return onDisk || System.currentTimeMillis() - startedAt > 15_000
    }
}
