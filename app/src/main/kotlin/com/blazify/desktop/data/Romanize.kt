package com.blazify.desktop.data

import com.ibm.icu.text.Transliterator

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Lyrics in the Latin alphabet, whatever they were written in.
 *
 * Plenty of people can sing along to a Hindi or Korean song without reading the
 * script it's written in — which makes a screen full of Devanagari lovely to
 * look at and no use at all for the one thing lyrics are for.
 *
 * The conversion is the standard one rather than anything invented here: a
 * table per language, written by hand, is a table per language got slightly
 * wrong.
 */
object Romanize {

    private val engine by lazy {
        // Any script to Latin, then the accents flattened. Without the second
        // step you get "mujhakō" — technically better and harder to read for
        // exactly the person who needed the first step.
        runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()
    }

    private val cache = mutableMapOf<String, String>()

    /**
     * Whether there's anything here worth converting.
     *
     * Latin text comes back from the converter unchanged, so running it would
     * be harmless — but checking first means an English song does no work at
     * all, and it keeps the setting from looking like it did something when it
     * didn't.
     */
    fun needed(text: String): Boolean = text.any { it.code > 0x24F }

    fun of(text: String): String {
        if (text.isBlank() || !needed(text)) return text
        cache[text]?.let { return it }
        val done = runCatching { engine?.transliterate(text) }.getOrNull() ?: text
        cache[text] = done
        return done
    }
}
