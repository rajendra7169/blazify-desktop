package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.prefs.Preferences

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** The accents on offer. Blaze amber is the default and the fallback. */
enum class Accent(val label: String, val head: Long, val tail: Long) {
    Blaze("Blaze", 0xFFFFA726, 0xFFFF7043),
    Ocean("Ocean", 0xFF29B6F6, 0xFF3F51B5),
    Forest("Forest", 0xFF66BB6A, 0xFF00897B),
    Rose("Rose", 0xFFF06292, 0xFFD81B60),
    Violet("Violet", 0xFFAB47BC, 0xFF6A1B9A),
    Sand("Sand", 0xFFD7CCC8, 0xFF8D6E63);

    val start: Color get() = Color(head)
    val end: Color get() = Color(tail)

    /**
     * Ink that stays readable on the accent itself.
     *
     * Decided by how bright the colour is rather than fixed, because a pale
     * accent with white text on it is unreadable and a dark one with black is
     * just as bad.
     */
    val ink: Color
        get() {
            val c = Color(head)
            val luminance = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            return if (luminance > 0.6f) Color(0xFF1A1005) else Color(0xFFFFFFFF)
        }
}

/** How the bar you drag is drawn. */
enum class SliderStyle(val label: String) {
    Capsule("Capsule"),
    Slim("Slim"),
    Squiggly("Squiggly"),
}

/** What sits behind the full player. */
enum class PlayerBackground(val label: String) {
    FollowTheme("Follow theme"),
    Gradient("Gradient"),
    PureBlack("Pure black"),
}

/** Where lyric lines sit across the panel. */
enum class LyricsAlign(val label: String) { Left("Left"), Centre("Centre"), Right("Right") }

/** How large the artwork on a shelf is drawn. */
enum class GridSize(val label: String, val art: Int) { Small("Small", 140), Big("Big", 172) }

/**
 * Everything about how the app looks, remembered between runs.
 *
 * Kept apart from the data folder on purpose: these are preferences about this
 * machine's screen, not about a library, and someone copying their library
 * across shouldn't drag a window's appearance along with it. The platform's own
 * preference store handles both places without any path handling.
 *
 * Every value here is read live by the thing it affects, so changing one shows
 * immediately rather than at the next launch.
 */
object Look {
    private val store = Preferences.userRoot().node("com/blazify/desktop/look")

    var accent by mutableStateOf(read("accent", Accent.Blaze) { Accent.valueOf(it) })
        private set

    var sliderStyle by mutableStateOf(read("slider", SliderStyle.Capsule) { SliderStyle.valueOf(it) })
        private set

    var playerBackground by mutableStateOf(
        read("playerBg", PlayerBackground.Gradient) { PlayerBackground.valueOf(it) },
    )
        private set

    var lyricsAlign by mutableStateOf(read("lyricsAlign", LyricsAlign.Left) { LyricsAlign.valueOf(it) })
        private set

    var gridSize by mutableStateOf(read("grid", GridSize.Big) { GridSize.valueOf(it) })
        private set

    /**
     * A darkness beyond the default one.
     *
     * The ordinary dark page is near-black rather than black, because a
     * saturated accent vibrates against pure #000. It's offered anyway — on an
     * OLED panel the true black is worth the trade, and it's a preference
     * rather than a mistake.
     */
    var pureBlack by mutableStateOf(store.getBoolean("pureBlack", false))
        private set

    var showGreeting by mutableStateOf(store.getBoolean("greeting", true))
        private set

    var startTab by mutableStateOf(read("startTab", Destination.Home) { Destination.valueOf(it) })
        private set

    fun chooseAccent(value: Accent) { accent = value; put("accent", value.name) }
    fun chooseSliderStyle(value: SliderStyle) { sliderStyle = value; put("slider", value.name) }
    fun choosePlayerBackground(value: PlayerBackground) { playerBackground = value; put("playerBg", value.name) }
    fun chooseLyricsAlign(value: LyricsAlign) { lyricsAlign = value; put("lyricsAlign", value.name) }
    fun chooseGridSize(value: GridSize) { gridSize = value; put("grid", value.name) }
    fun chooseStartTab(value: Destination) { startTab = value; put("startTab", value.name) }

    fun choosePureBlack(value: Boolean) {
        pureBlack = value
        runCatching { store.putBoolean("pureBlack", value) }
    }

    fun chooseShowGreeting(value: Boolean) {
        showGreeting = value
        runCatching { store.putBoolean("greeting", value) }
    }

    /** Back to how it shipped, for anyone who has painted themselves into a corner. */
    fun reset() {
        chooseAccent(Accent.Blaze)
        chooseSliderStyle(SliderStyle.Capsule)
        choosePlayerBackground(PlayerBackground.Gradient)
        chooseLyricsAlign(LyricsAlign.Left)
        chooseGridSize(GridSize.Big)
        choosePureBlack(false)
        chooseShowGreeting(true)
        chooseStartTab(Destination.Home)
    }

    /** A stored name that no longer exists falls back rather than crashing. */
    private fun <T> read(key: String, fallback: T, parse: (String) -> T): T =
        runCatching { parse(store.get(key, "")) }.getOrDefault(fallback)

    private fun put(key: String, value: String) {
        runCatching { store.put(key, value) }
    }
}
