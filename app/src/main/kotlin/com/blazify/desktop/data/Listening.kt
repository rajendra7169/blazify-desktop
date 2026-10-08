package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * How the music itself is heard: how fast, whether its key follows, and which
 * parts of it are passed over.
 *
 * Kept apart from [Playback], which is about what the player does between
 * songs. These change the song while it plays, and all of them start where the
 * record was made — at its own speed, in its own key, every second of it.
 */
object Listening {

    private val store = File(Store.folder, "listening")

    /**
     * How fast music plays, from half to double.
     *
     * Talk has its own speed, chosen beside the episode; this is for songs.
     * Somebody practising an instrument slows a solo down, somebody running
     * speeds a playlist up, and neither of them wants the other's number
     * carried over to a podcast.
     */
    var speed by mutableStateOf(1f)
        private set

    /**
     * Whether a song keeps its key when its speed changes.
     *
     * On, which is what almost everybody means by "faster". Off is the record
     * player: faster is higher and slower is lower, which some people want
     * precisely because it is how a sped-up song has always sounded.
     */
    var naturalPitch by mutableStateOf(true)
        private set

    /**
     * The pitch rule as it was when the player was built.
     *
     * It belongs to the machinery rather than to a track — the player is told
     * once, when it starts, whether to keep the key — so a change waits for
     * the next launch, and saying so needs to know what it started with.
     */
    val startedNatural: Boolean

    /**
     * Which stretches marked by viewers are jumped over.
     *
     * None to begin with. Asking means telling a public database which video
     * is playing — by a hash, but still asking — and that is somebody's own
     * choice to make.
     */
    var skipKinds by mutableStateOf(emptySet<SponsorBlock.Kind>())
        private set

    init {
        runCatching {
            if (store.exists()) {
                val lines = store.readLines()
                speed = lines.getOrNull(0)?.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1f
                naturalPitch = lines.getOrNull(1) != "false"
                skipKinds = lines.getOrNull(2).orEmpty().split(',')
                    .mapNotNull { name -> SponsorBlock.Kind.entries.firstOrNull { it.name == name } }
                    .toSet()
            }
        }
        startedNatural = naturalPitch
    }

    /** The speeds worth offering, which is not every number between them. */
    val speeds = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

    fun chooseSpeed(value: Float) { speed = value.coerceIn(0.5f, 2f); save() }
    fun chooseNaturalPitch(value: Boolean) { naturalPitch = value; save() }
    fun chooseSkip(kind: SponsorBlock.Kind, on: Boolean) {
        skipKinds = if (on) skipKinds + kind else skipKinds - kind
        save()
    }

    /** Whether the pitch rule differs from the one the player was built with. */
    val pitchWaitsForRestart: Boolean
        get() = naturalPitch != startedNatural

    fun reset() {
        speed = 1f
        naturalPitch = true
        save()
    }

    private fun save() {
        runCatching {
            store.writeText(
                listOf(
                    speed.toString(), naturalPitch.toString(),
                    skipKinds.joinToString(",") { it.name },
                ).joinToString("\n"),
            )
        }
    }
}
