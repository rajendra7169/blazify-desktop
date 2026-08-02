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
     * The scripts this can convert, each with the block of characters that
     * gives it away.
     *
     * Offered one by one rather than as a single switch: someone who reads
     * Devanagari but not Hangul wants exactly one of these converted, and a
     * single switch makes them choose between two languages they read
     * differently.
     */
    enum class Script(val label: String, private val ranges: List<IntRange>) {
        Hindi("Hindi", listOf(0x0900..0x097F)),
        Japanese("Japanese", listOf(0x3040..0x30FF, 0x31F0..0x31FF)),
        Korean("Korean", listOf(0xAC00..0xD7AF, 0x1100..0x11FF, 0x3130..0x318F)),
        Chinese("Chinese", listOf(0x4E00..0x9FFF, 0x3400..0x4DBF)),
        Cyrillic("Cyrillic", listOf(0x0400..0x04FF)),
        Greek("Greek", listOf(0x0370..0x03FF)),
        Arabic("Arabic", listOf(0x0600..0x06FF)),
        Thai("Thai", listOf(0x0E00..0x0E7F));

        fun matches(text: String) = text.any { c -> ranges.any { c.code in it } }
    }

    /**
     * Which script a line is written in, if any this can handle.
     *
     * Japanese is checked before Chinese because the two share their
     * characters — a line with kana in it is Japanese whatever else it holds,
     * and testing the shared block first would call all of it Chinese.
     */
    fun scriptOf(text: String): Script? = Script.entries.firstOrNull { it.matches(text) }

    /**
     * Whether there's anything here worth converting.
     *
     * Latin text comes back from the converter unchanged, so running it would
     * be harmless — but checking first means an English song does no work at
     * all, and it keeps the setting from looking like it did something when it
     * didn't.
     */
    fun needed(text: String): Boolean = text.any { it.code > 0x24F }

    fun of(text: String, allowed: Set<String>): String {
        if (text.isBlank() || !needed(text)) return text
        val script = scriptOf(text) ?: return text
        if (script.label !in allowed) return text
        cache[text]?.let { return it }
        val done = runCatching { engine?.transliterate(text) }.getOrNull() ?: text
        cache[text] = done
        return done
    }
}
