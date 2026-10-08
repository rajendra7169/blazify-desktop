package com.blazify.desktop.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

class AutoEqTest {

    @Test
    fun readsTheIndex() {
        val models = AutoEq.parseIndex(
            """
            # Index
            - [Sennheiser HD 600](./oratory1990/over-ear/Sennheiser%20HD%20600) by oratory1990
            - [1MORE Aero (ANC Off)](./HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)) by HypetheSonics on GRAS RA0045
            """.trimIndent(),
        )
        assertEquals(2, models.size)
        assertEquals("Sennheiser HD 600", models[0].name)
        assertEquals("./oratory1990/over-ear/Sennheiser%20HD%20600", models[0].path)
        assertEquals("./HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)", models[1].path)
    }

    @Test
    fun readsAGraphicEqCurve() {
        val curve = AutoEq.parseGraphicEq("GraphicEQ: 20 -0.2; 100 -5.4; 1000 1.5; 20000 -3")
        assertEquals(listOf(20f to -0.2f, 100f to -5.4f, 1000f to 1.5f, 20000f to -3f), curve)
    }

    @Test
    fun mapsOntoBandsWithoutClipping() {
        // Flat at 0 dB below 1 kHz and +6 dB above it.
        val curve = listOf(20f to 0f, 1000f to 0f, 2000f to 6f, 20000f to 6f)
        val centres = listOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
        val (gains, preamp) = AutoEq.toBands(curve, centres)

        assertEquals(10, gains.size)
        // The shape survives: the top bands sit six above the bottom ones.
        assertEquals(6f, gains.last() - gains.first(), 0.05f)
        // And nothing ends up louder than it started.
        assertTrue(gains.max() + preamp <= 0.05f)
    }

    @Test
    fun interpolatesOnALogScale() {
        // Half-way between 100 Hz and 10 kHz on a log scale is 1 kHz.
        val curve = listOf(100f to 0f, 10000f to 10f)
        val (gains, _) = AutoEq.toBands(curve, listOf(100f, 1000f, 10000f))
        assertEquals(5f, gains[1] - gains[0], 0.05f)
    }
}
