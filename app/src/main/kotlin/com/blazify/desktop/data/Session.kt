package com.blazify.desktop.data

import kotlinx.serialization.Serializable

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** A queue as it stood, and how far into it you had got. */
@Serializable
data class Session(
    val tracks: List<Track>,
    val index: Int,
    val seconds: Double,
)

/**
 * What was playing when the window closed.
 *
 * [Resume] deliberately remembers only long things — a three minute song that
 * starts two thirds of the way through is an irritation rather than a
 * courtesy. But that reasoning is about one recording, and it left the whole
 * queue unremembered: closing the app lost the album, the radio and everything
 * queued behind it, which is not a kindness to anybody.
 *
 * So the queue is kept as well, with the position of the song that was
 * playing. Reopening puts it all back, paused, exactly where it stopped.
 * Paused because opening a music player should never start music on its own —
 * a window that begins playing when it appears is a window opened by accident
 * at work.
 */
object LastSession {

    private const val FILE = "session.json"

    fun save(tracks: List<Track>, index: Int, seconds: Double) {
        if (tracks.isEmpty()) {
            Store.write<Session>(FILE, emptyList())
            return
        }
        Store.write(FILE, listOf(Session(tracks, index, seconds)))
    }

    /** What was on last time, or nothing on a first run. */
    fun load(): Session? = Store.read<Session>(FILE).firstOrNull()?.takeIf {
        it.tracks.isNotEmpty() && it.index in it.tracks.indices
    }

    fun clear() = Store.write<Session>(FILE, emptyList())
}
