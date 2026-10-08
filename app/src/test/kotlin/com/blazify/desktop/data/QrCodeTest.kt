package com.blazify.desktop.data

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The QR encoder, checked against the standard's own worked numbers and
 * against squares another, long-established encoder produced for the same
 * input, level and mask.
 *
 * A code that is one module wrong does not scan at all, and nobody looking at
 * it can tell — so this is checked module for module, not by eye.
 */
class QrCodeTest {

    private fun rows(code: QrCode) = (0 until code.size).joinToString("\n") { y ->
        (0 until code.size).joinToString("") { x -> if (code[x, y]) "1" else "0" }
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    @Test
    fun `correction codewords match the worked example in the standard`() {
        // "HELLO WORLD" at 1-M: sixteen data codewords and the ten that protect them.
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
            .map { it.toByte() }.toByteArray()
        val ecc = QrCode.reedSolomonRemainder(data, QrCode.reedSolomonDivisor(10))
        assertContentEquals(
            intArrayOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23),
            ecc.map { it.toInt() and 0xFF }.toIntArray(),
        )
    }

    @Test
    fun `format and version labels match the standard's tables`() {
        assertEquals(0b111011111000100, QrCode.formatBits(QrCode.Level.L, 0))
        assertEquals(0b101010000010010, QrCode.formatBits(QrCode.Level.M, 0))
        assertEquals(0b100000011001110, QrCode.formatBits(QrCode.Level.M, 5))
        assertEquals(0b000111110010010100, QrCode.versionBits(7))
        assertEquals(0b101000110001101001, QrCode.versionBits(40))
    }

    @Test
    fun `alignment squares sit where the standard puts them`() {
        assertContentEquals(intArrayOf(), QrCode.alignmentPositions(1))
        assertContentEquals(intArrayOf(6, 18), QrCode.alignmentPositions(2))
        assertContentEquals(intArrayOf(6, 22, 38), QrCode.alignmentPositions(7))
        assertContentEquals(intArrayOf(6, 34, 60, 86, 112, 138), QrCode.alignmentPositions(32))
        assertContentEquals(intArrayOf(6, 30, 58, 86, 114, 142, 170), QrCode.alignmentPositions(40))
    }

    @Test
    fun `capacities match the published byte-mode figures`() {
        assertEquals(17, QrCode.capacity(1, QrCode.Level.L))
        assertEquals(14, QrCode.capacity(1, QrCode.Level.M))
        assertEquals(1732, QrCode.capacity(30, QrCode.Level.L))
        assertEquals(2953, QrCode.capacity(40, QrCode.Level.L))
        assertEquals(2331, QrCode.capacity(40, QrCode.Level.M))
    }

    @Test
    fun `a short link comes out exactly as another encoder draws it`() {
        val code = assertNotNull(QrCode.encode("https://example.com".toByteArray(), QrCode.Level.M, forcedMask = 3))
        assertEquals(2, code.version)
        assertEquals(
            """
            1111111010011100101111111
            1000001011111100101000001
            1011101000111111101011101
            1011101010000111001011101
            1011101000111111101011101
            1000001001111110101000001
            1111111010101010101111111
            0000000011011001100000000
            1011011101100101101001011
            0100110010110100010100010
            0100111000011100111110000
            0000010111011000000001100
            0111001000011011011010111
            0100110001111011111110001
            0101101001010100100010110
            1001000100010011111110001
            0001001000101011111111111
            0000000010100010100010101
            1111111010000110101010111
            1000001010010111100010001
            1011101000000110111111000
            1011101010000001011011111
            1011101010100010011010110
            1000001001111111011010100
            1111111011010000011111111
            """.trimIndent(),
            rows(code),
        )
    }

    @Test
    fun `a full shared playlist comes out exactly as another encoder draws it`() {
        val link = "https://rajendra7169.github.io/blazify/playlist#1.TXkgbWl4." + "dQw4w9WgXcQ".repeat(150)
        val code = assertNotNull(QrCode.encode(link.toByteArray(), QrCode.Level.M, forcedMask = 6))
        assertEquals(34, code.version)
        assertEquals(153, code.size)
        assertEquals("bdcf2de67002fe268ec4f03e222ed8ac8dfc0ecee8585b46edf97cb98e4ffe5e", sha256(rows(code)))
    }

    @Test
    fun `the longest link that can be shared still fits, and anything past the limit says so`() {
        val link = "https://rajendra7169.github.io/blazify/playlist#1." + "A".repeat(60) + "." +
            "dQw4w9WgXcQ".repeat(150)
        val code = assertNotNull(QrCode.encode(link))
        assertEquals(QrCode.Level.M, code.level)
        assertTrue(code.mask in 0..7)
        assertNull(QrCode.encode("x".repeat(3000)))
    }
}
