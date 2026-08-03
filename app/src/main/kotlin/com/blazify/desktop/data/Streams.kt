package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.innertube.models.YouTubeClient
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * How the audio itself is asked for.
 *
 * The catalogue answers differently depending on which of its own clients you
 * say you are: one will hand over a stream another refuses, one carries higher
 * bitrates, and which of them works changes over time as the service is
 * altered. So the list is a list, tried in order, and it is yours to reorder —
 * when playback breaks for a particular song, moving a source up is the fix,
 * and waiting for somebody to ship a new build is not.
 */
object Streams {

    /** One way of asking, with what it is good for. */
    /**
     * The ways of asking, in the order they are tried.
     *
     * Answering is not the same as being usable. Some clients hand over a
     * perfectly good-looking link that this machine is then refused when it
     * tries to fetch it — the link is tied to the kind of device that asked,
     * and a desktop is not that device. Those are listed but off by default,
     * because a source that answers and cannot be played is worse than one that
     * does not answer at all: it looks like the song is broken.
     */
    enum class Source(
        val label: String,
        val blurb: String,
        val client: YouTubeClient,
        /** Whether this machine can actually fetch what this source hands over. */
        val usable: Boolean = true,
    ) {
        Headset("Headset", "The one that plays here, and at the highest bitrates", YouTubeClient.ANDROID_VR_NO_AUTH),
        Visual("Visual", "A second opinion when the first is refused", YouTubeClient.VISIONOS),
        Studio("Studio", "Reaches some things the others are refused", YouTubeClient.ANDROID_CREATOR),
        Browser("Browser", "The plain web client; last resort", YouTubeClient.WEB_REMIX),
        Handheld(
            "Handheld",
            "Answers, but its links are refused to anything that isn't a phone",
            YouTubeClient.IOS,
            usable = false,
        ),
        Tablet(
            "Tablet",
            "The same again, and refused the same way",
            YouTubeClient.IPADOS,
            usable = false,
        ),
    }

    /** How good a stream to take when several are offered. */
    enum class Quality(val label: String, val blurb: String) {
        Best("Best", "The highest bitrate on offer"),
        Balanced("Balanced", "Around 128kbps — near-transparent, half the data"),
        Saver("Saver", "The lowest on offer, for a metered connection"),
    }

    private val store = File(Store.folder, "streams")

    var order by mutableStateOf(Source.entries.map { it.name })
        private set

    var enabled by mutableStateOf(Source.entries.filter { it.usable }.map { it.name }.toSet())
        private set

    var quality by mutableStateOf(Quality.Best)
        private set

    init {
        runCatching {
            if (!store.exists()) return@runCatching
            val lines = store.readLines()
            lines.getOrNull(0)?.split(",")?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }?.let { order = it }
            lines.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }?.let { enabled = it.toSet() }
            lines.getOrNull(2)?.let { name ->
                Quality.entries.firstOrNull { it.name == name }?.let { quality = it }
            }
        }
    }

    private fun save() {
        runCatching {
            store.writeText(
                listOf(order.joinToString(","), enabled.joinToString(","), quality.name)
                    .joinToString("\n"),
            )
        }
    }

    fun chooseOrder(value: List<String>) { order = value; save() }
    fun chooseEnabled(value: Set<String>) { enabled = value; save() }
    fun chooseQuality(value: Quality) { quality = value; save() }

    fun reset() {
        order = Source.entries.map { it.name }
        enabled = Source.entries.filter { it.usable }.map { it.name }.toSet()
        quality = Quality.Best
        save()
    }

    /**
     * The sources to try, in order.
     *
     * Anything the saved order doesn't mention goes on the end rather than
     * disappearing — which is what lets a source added later show up for
     * somebody who arranged this list before it existed.
     */
    fun chain(): List<Source> {
        val known = Source.entries
        val ordered = order.mapNotNull { name -> known.firstOrNull { it.name == name } }
        return (ordered + known.filterNot { it in ordered }).filter { it.name in enabled }
    }

    /**
     * Pick a stream out of what a source offered.
     *
     * Best takes the top bitrate. Saver takes the bottom. Balanced aims at
     * 128kbps and takes whichever is nearest — the point at which most people
     * stop being able to tell, on most equipment, for half the data.
     */
    fun <T> pick(formats: List<T>, bitrateOf: (T) -> Int): T? = when (quality) {
        Quality.Best -> formats.maxByOrNull(bitrateOf)
        Quality.Saver -> formats.minByOrNull(bitrateOf)
        Quality.Balanced -> formats.minByOrNull { kotlin.math.abs(bitrateOf(it) - 128_000) }
    }
}
