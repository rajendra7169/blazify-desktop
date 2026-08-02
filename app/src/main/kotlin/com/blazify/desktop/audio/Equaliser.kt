package com.blazify.desktop.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.prefs.Preferences

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Shaping the sound.
 *
 * The bands and the presets both come from the audio library rather than being
 * invented here — it is the thing doing the work, and a preset called "Rock"
 * that doesn't match the one the library applies would be a lie in the label.
 *
 * Off is a real state, not a flat curve: an equaliser that is switched off is
 * detached from the player entirely, so it costs nothing when unused.
 */
object Equaliser {

    private val store = Preferences.userRoot().node("com/blazify/desktop/eq")

    /** Centre frequencies, in hertz, as the library reports them. */
    var bands by mutableStateOf<List<Float>>(emptyList())
        private set

    /** Named curves the library ships with. */
    var presets by mutableStateOf<List<String>>(emptyList())
        private set

    var on by mutableStateOf(store.getBoolean("on", false))
        private set

    /** The preset in use, or null when the bands have been moved by hand. */
    var preset by mutableStateOf<String?>(store.get("preset", "").ifBlank { null })
        private set

    /** Gain per band in decibels, and the overall level. */
    var gains by mutableStateOf<List<Float>>(emptyList())
        private set

    var preamp by mutableStateOf(store.getFloat("preamp", 0f))
        private set

    /** How far a band can be pushed either way. */
    const val RANGE = 20f

    /**
     * Read what the library offers.
     *
     * Deferred until something asks, because touching it loads the native
     * library — the same reason the player itself is built lazily.
     */
    fun load() {
        if (bands.isNotEmpty()) return
        runCatching {
            val api = AudioEngine.equalizerApi() ?: return
            bands = api.bands()
            presets = api.presets()
            gains = readGains(bands.size)
            if (on) apply()
        }
    }

    fun setEnabled(value: Boolean) {
        on = value
        runCatching { store.putBoolean("on", value) }
        apply()
    }

    fun choosePreset(name: String) {
        preset = name
        runCatching { store.put("preset", name) }
        runCatching {
            val api = AudioEngine.equalizerApi() ?: return
            val curve = api.newEqualizer(name) ?: return
            gains = (0 until curve.bandCount()).map { curve.amp(it) }
            preamp = curve.preamp()
            saveGains()
        }
        if (!on) setEnabled(true) else apply()
    }

    /**
     * Move one band.
     *
     * The preset is dropped the moment a band moves: what's playing is no
     * longer that curve, and leaving the name showing would misdescribe it.
     */
    fun setBand(index: Int, value: Float) {
        if (index !in gains.indices) return
        gains = gains.toMutableList().also { it[index] = value.coerceIn(-RANGE, RANGE) }
        preset = null
        runCatching { store.remove("preset") }
        saveGains()
        if (on) apply()
    }

    fun changePreamp(value: Float) {
        preamp = value.coerceIn(-RANGE, RANGE)
        runCatching { store.putFloat("preamp", preamp) }
        if (on) apply()
    }

    fun flatten() {
        gains = List(bands.size) { 0f }
        preamp = 0f
        preset = null
        runCatching { store.remove("preset") }
        saveGains()
        if (on) apply()
    }

    /** Push the current curve at the player, or detach it. */
    private fun apply() {
        runCatching {
            if (!on) {
                AudioEngine.applyEqualizer(null)
                return
            }
            val api = AudioEngine.equalizerApi() ?: return
            val curve = api.newEqualizer() ?: return
            curve.setPreamp(preamp)
            gains.forEachIndexed { at, gain -> if (at < curve.bandCount()) curve.setAmp(at, gain) }
            AudioEngine.applyEqualizer(curve)
        }
    }

    private fun readGains(count: Int): List<Float> =
        (0 until count).map { store.getFloat("band$it", 0f) }

    private fun saveGains() {
        runCatching { gains.forEachIndexed { at, gain -> store.putFloat("band$at", gain) } }
    }
}
