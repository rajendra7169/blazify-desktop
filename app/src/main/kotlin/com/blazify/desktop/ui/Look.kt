package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.blazify.desktop.data.LyricsProvider
import com.blazify.desktop.data.LyricsProviders
import com.blazify.desktop.data.Romanize
import java.util.prefs.Preferences

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

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

/**
 * How the line being sung announces itself.
 *
 * The plain one only changes colour. The rest are progressively less quiet, and
 * Blaze is the app's own: the line lifts, brightens and carries the record's
 * colour behind it at once. Offered rather than chosen for people, because how
 * much movement is pleasant and how much is distracting is not a thing anyone
 * can decide on someone else's behalf.
 */
enum class LyricsStyle(val label: String, val blurb: String) {
    Plain("Plain", "Colour only — nothing moves"),
    Fade("Fade", "Lines ease in and out as they pass"),
    Lift("Lift", "The sung line grows and slides forward"),
    Blaze("Blaze", "Lift, glow and the record's own colour behind it"),
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

    private var chosen by mutableStateOf(Accent.named(store.get("accent", Accent.Blaze.label)))

    /**
     * Whether the accent follows the artwork.
     *
     * On by default, as it is on the phone: a cover is the one thing on screen
     * that already has an identity, and taking the accent from it makes the
     * window belong to the song rather than to the application.
     *
     * The chosen colour is kept underneath rather than overwritten, so turning
     * this off puts back the one that was picked instead of leaving whatever
     * the last cover happened to be.
     */
    var dynamicColour by mutableStateOf(store.getBoolean("dynamic", true))
        private set

    /**
     * Whether the window itself takes the artwork's hue, not just the accent.
     *
     * The difference between an application showing a record and an
     * application that looks like the record.
     */
    var tintedWindow by mutableStateOf(store.getBoolean("tinted", true))
        private set

    fun chooseTintedWindow(value: Boolean) {
        tintedWindow = value
        runCatching { store.putBoolean("tinted", value) }
    }

    /** The accent in force: the artwork's when it's following one, else yours. */
    val accent: Accent
        get() = if (dynamicColour) ArtworkColour.accent ?: chosen else chosen

    /** What was picked by hand, whatever the artwork is currently saying. */
    val picked: Accent get() = chosen

    var sliderStyle by mutableStateOf(read("slider", SliderStyle.Capsule) { SliderStyle.valueOf(it) })
        private set

    var playerBackground by mutableStateOf(
        read("playerBg", PlayerBackground.Gradient) { PlayerBackground.valueOf(it) },
    )
        private set

    var lyricsAlign by mutableStateOf(read("lyricsAlign", LyricsAlign.Left) { LyricsAlign.valueOf(it) })
        private set

    /** Extra air between lines, on top of what the type needs. */
    var lyricsSpacing by mutableStateOf(store.getInt("lyricsSpacing", 7))
        private set

    /**
     * Whether the words are turned into the Latin alphabet.
     *
     * Off by default: someone who reads the script wants the script, and
     * guessing otherwise would be presumptuous about who's listening.
     */
    var romanize by mutableStateOf(store.getBoolean("romanize", false))
        private set

    /** A glow behind the line being sung, which is the app's own look. */
    var lyricsGlow by mutableStateOf(store.getBoolean("lyricsGlow", false))
        private set

    /**
     * Which scripts get turned into Latin letters.
     *
     * All of them to begin with, since someone who turned the setting on meant
     * it — and any they can read, they can turn back off one at a time.
     */
    var romanized by mutableStateOf(
        store.get("romanized", Romanize.Script.entries.joinToString(",") { it.label })
            .split(",").filter { it.isNotBlank() }.toSet(),
    )
        private set

    fun chooseRomanized(value: Set<String>) {
        romanized = value
        put("romanized", value.joinToString(","))
    }

    /**
     * How far ahead of the reported position the words are read, in seconds.
     *
     * Sound leaves the player some time before it leaves the speakers, and the
     * position we are told is where the player has got to, not what you can
     * hear. Without an allowance for that gap every line lights up just after
     * it has been sung.
     */
    var lyricsLead by mutableStateOf(store.getFloat("lyricsLead", 0.45f))
        private set

    fun chooseLyricsLead(value: Float) {
        lyricsLead = value.coerceIn(0f, 2f)
        runCatching { store.putFloat("lyricsLead", lyricsLead) }
    }

    /**
     * Which sources may be asked for words, and in what order.
     *
     * Both are kept as names rather than positions so that adding a source
     * later doesn't quietly reshuffle the list someone arranged, and a name no
     * longer known is dropped on the way out instead of leaving a hole.
     */
    var lyricsSources by mutableStateOf(
        store.get("lyricsSources", LyricsProviders.all.joinToString(",") { it.name })
            .split(",").filter { it.isNotBlank() }.toSet(),
    )
        private set

    var lyricsOrder by mutableStateOf(
        store.get("lyricsOrder", LyricsProviders.all.joinToString(",") { it.name })
            .split(",").filter { it.isNotBlank() },
    )
        private set

    fun chooseLyricsSources(value: Set<String>) {
        lyricsSources = value
        put("lyricsSources", value.joinToString(","))
    }

    fun chooseLyricsOrder(value: List<String>) {
        lyricsOrder = value
        put("lyricsOrder", value.joinToString(","))
    }

    /**
     * The sources to ask, in order.
     *
     * Anything the saved order doesn't mention goes on the end rather than
     * being lost — that is what makes a new source appear for people who
     * arranged the list before it existed.
     */
    fun lyricsChain(): List<LyricsProvider> {
        val known = LyricsProviders.all
        val ordered = lyricsOrder.mapNotNull { name -> known.firstOrNull { it.name == name } }
        return (ordered + known.filterNot { it in ordered }).filter { it.name in lyricsSources }
    }

    /**
     * Which of the looks the sheet uses.
     *
     * Blaze by default: it is the one that was designed with the rest of the
     * app rather than the one that asks least of it.
     */
    var lyricsStyle by mutableStateOf(read("lyricsStyle", LyricsStyle.Blaze) { LyricsStyle.valueOf(it) })
        private set

    fun chooseLyricsStyle(value: LyricsStyle) { lyricsStyle = value; put("lyricsStyle", value.name) }

    /** Whether clicking a line jumps playback to it. */
    var lyricsTap by mutableStateOf(store.getBoolean("lyricsTap", true))
        private set

    fun chooseLyricsTap(value: Boolean) {
        lyricsTap = value
        runCatching { store.putBoolean("lyricsTap", value) }
    }

    /**
     * How much of a lift the line being sung gets over the rest.
     *
     * A multiplier on the line height rather than a gap in points, so the sheet
     * keeps its proportions when the type is made larger — which is the whole
     * reason someone made it larger.
     */
    var lyricsLineHeight by mutableStateOf(store.getFloat("lyricsLineHeight", 1.5f))
        private set

    fun chooseLyricsLineHeight(value: Float) {
        lyricsLineHeight = value.coerceIn(1.0f, 3.0f)
        runCatching { store.putFloat("lyricsLineHeight", lyricsLineHeight) }
    }

    /** Point size of the words, so the sheet can be read from across a room. */
    var lyricsPoints by mutableStateOf(store.getFloat("lyricsPoints", 17f))
        private set

    fun chooseLyricsPoints(value: Float) {
        lyricsPoints = value.coerceIn(12f, 48f)
        runCatching { store.putFloat("lyricsPoints", lyricsPoints) }
    }

    /** Whether the words follow along on their own. */
    var lyricsFollow by mutableStateOf(store.getBoolean("lyricsFollow", true))
        private set


    fun chooseLyricsSpacing(value: Int) {
        lyricsSpacing = value.coerceIn(0, 28)
        runCatching { store.putInt("lyricsSpacing", lyricsSpacing) }
    }

    fun chooseRomanize(value: Boolean) {
        romanize = value
        runCatching { store.putBoolean("romanize", value) }
    }

    fun chooseLyricsGlow(value: Boolean) {
        lyricsGlow = value
        runCatching { store.putBoolean("lyricsGlow", value) }
    }

    fun chooseLyricsFollow(value: Boolean) {
        lyricsFollow = value
        runCatching { store.putBoolean("lyricsFollow", value) }
    }

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

    fun chooseAccent(value: Accent) { chosen = value; put("accent", value.label) }

    fun chooseDynamicColour(value: Boolean) {
        dynamicColour = value
        runCatching { store.putBoolean("dynamic", value) }
    }
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
    /**
     * Defaults, one group at a time.
     *
     * Put back beside the heading they belong to rather than only all at once:
     * the whole point of trying an alignment is that you can undo it without
     * also losing the accent you spent five minutes choosing.
     */
    fun resetAccent() {
        chooseAccent(Accent.Blaze)
        chooseDynamicColour(true)
        chooseTintedWindow(true)
    }

    fun resetSlider() = chooseSliderStyle(SliderStyle.Capsule)

    fun resetPlayer() = choosePlayerBackground(PlayerBackground.Gradient)

    fun resetShelves() = chooseGridSize(GridSize.Big)

    fun resetOpening() {
        chooseStartTab(Destination.Home)
        choosePureBlack(false)
        chooseShowGreeting(true)
    }

    fun resetLyricsSources() {
        chooseLyricsSources(LyricsProviders.all.map { it.name }.toSet())
        chooseLyricsOrder(LyricsProviders.all.map { it.name })
    }

    fun resetLyricsType() {
        chooseLyricsAlign(LyricsAlign.Left)
        chooseLyricsPoints(17f)
        chooseLyricsLineHeight(1.5f)
        chooseLyricsSpacing(7)
    }

    fun resetLyricsPlayback() {
        chooseLyricsStyle(LyricsStyle.Blaze)
        chooseLyricsFollow(true)
        chooseLyricsTap(true)
        chooseLyricsGlow(false)
        chooseLyricsLead(0.45f)
    }

    fun resetRomanize() {
        chooseRomanize(false)
        chooseRomanized(Romanize.Script.entries.map { it.label }.toSet())
    }

    fun reset() {
        chooseLyricsStyle(LyricsStyle.Blaze)
        chooseAccent(Accent.Blaze)
        chooseDynamicColour(true)
        chooseTintedWindow(true)
        chooseSliderStyle(SliderStyle.Capsule)
        choosePlayerBackground(PlayerBackground.Gradient)
        chooseLyricsAlign(LyricsAlign.Left)
        chooseLyricsPoints(17f)
        chooseLyricsLineHeight(1.5f)
        chooseLyricsTap(true)
        chooseLyricsLead(0.45f)
        chooseLyricsSources(LyricsProviders.all.map { it.name }.toSet())
        chooseLyricsOrder(LyricsProviders.all.map { it.name })
        chooseLyricsSpacing(7)
        chooseRomanize(false)
        chooseLyricsGlow(false)
        chooseRomanized(Romanize.Script.entries.map { it.label }.toSet())
        chooseLyricsLead(0.45f)
        chooseLyricsFollow(true)
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
