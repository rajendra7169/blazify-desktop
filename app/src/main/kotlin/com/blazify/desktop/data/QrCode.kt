package com.blazify.desktop.data

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A QR code, worked out here rather than fetched or borrowed.
 *
 * Only what sharing a link needs: text goes in as bytes, the code is the
 * smallest version that holds it, and the error correction is medium where
 * that fits and low where it does not. A link to a long playlist runs to
 * seventeen hundred characters, which is a large code but a perfectly
 * ordinary one — a camera reads version 30 from across a desk.
 *
 * Nothing here draws. The answer is a square of dark and light modules, and
 * whoever asked paints it however suits them.
 */
class QrCode private constructor(
    /** 1 to 40; how many modules a side is follows from it. */
    val version: Int,
    val level: Level,
    val mask: Int,
    private val modules: Array<BooleanArray>,
) {
    val size: Int get() = modules.size

    /** Whether the module at column [x], row [y] is dark. */
    operator fun get(x: Int, y: Int): Boolean = modules[y][x]

    /**
     * How much damage a code can survive.
     *
     * Only the two lower levels. The higher ones exist for codes printed on
     * crates and scuffed by forklifts; a code on a screen is clean, and every
     * step up roughly halves what fits.
     */
    enum class Level(val bits: Int) { L(1), M(0) }

    companion object {

        /**
         * Encode some text, or say it doesn't fit.
         *
         * Medium first, low if medium won't hold it. Null rather than an
         * exception when even low won't — that is a link too long to share
         * this way, which is something to tell the person, not a crash.
         */
        fun encode(text: String): QrCode? =
            encode(text.toByteArray(Charsets.UTF_8), Level.M) ?: encode(text.toByteArray(Charsets.UTF_8), Level.L)

        /**
         * Encode bytes at one level, optionally with a mask chosen in advance.
         *
         * Choosing the mask is only for checking this against another encoder
         * module for module; left alone, the one that reads best is picked.
         */
        fun encode(data: ByteArray, level: Level, forcedMask: Int? = null): QrCode? {
            val version = (1..40).firstOrNull { fits(data.size, it, level) } ?: return null
            val size = version * 4 + 17
            val grid = Grid(size)
            grid.drawFunctionPatterns(version, level)
            grid.drawCodewords(withCorrection(codewords(data, version, level), version, level))

            val mask = forcedMask ?: (0..7).minBy { candidate ->
                grid.applyMask(candidate)
                grid.drawFormat(level, candidate)
                val penalty = grid.penalty()
                grid.applyMask(candidate)   // a mask is its own undo
                penalty
            }
            grid.applyMask(mask)
            grid.drawFormat(level, mask)
            return QrCode(version, level, mask, grid.modules)
        }

        /** The most bytes one version holds at one level, in byte mode. */
        fun capacity(version: Int, level: Level): Int {
            val bits = dataCodewords(version, level) * 8 - 4 - countBits(version)
            return bits / 8
        }

        private fun fits(bytes: Int, version: Int, level: Level) = bytes <= capacity(version, level)

        /** Byte mode counts its characters in 8 bits up to version 9, then 16. */
        private fun countBits(version: Int) = if (version <= 9) 8 else 16

        // ---- Codewords --------------------------------------------------------

        /**
         * The message as the code carries it: a mode, a length, the bytes, an
         * end marker and then the two padding bytes the standard asks for,
         * alternated until every data codeword is spoken for.
         */
        internal fun codewords(data: ByteArray, version: Int, level: Level): ByteArray {
            val bits = Bits()
            bits.add(0b0100, 4)
            bits.add(data.size, countBits(version))
            data.forEach { bits.add(it.toInt() and 0xFF, 8) }

            val capacityBits = dataCodewords(version, level) * 8
            bits.add(0, minOf(4, capacityBits - bits.length))
            bits.add(0, (8 - bits.length % 8) % 8)
            var pad = 0xEC
            while (bits.length < capacityBits) {
                bits.add(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            return bits.toBytes()
        }

        /**
         * Split into blocks, give each its correction, and deal them out.
         *
         * The interleaving is the point of the blocks: a smudge across one
         * part of the code then costs a little of every block instead of all
         * of one, and every block can recover a little.
         */
        internal fun withCorrection(data: ByteArray, version: Int, level: Level): ByteArray {
            val blocks = BLOCKS[level.ordinal][version]
            val eccLength = ECC_PER_BLOCK[level.ordinal][version]
            val raw = rawDataModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLength = raw / blocks

            val divisor = reedSolomonDivisor(eccLength)
            val made = ArrayList<ByteArray>(blocks)
            var at = 0
            for (i in 0 until blocks) {
                val take = shortLength - eccLength + if (i < shortBlocks) 0 else 1
                val part = data.copyOfRange(at, at + take)
                at += take
                val ecc = reedSolomonRemainder(part, divisor)
                // The short blocks carry a gap where the long ones have one
                // more byte, so every block can be read across by position.
                val padded = if (i < shortBlocks) part + 0.toByte() else part
                made += padded + ecc
            }

            val out = ByteArray(raw)
            var k = 0
            for (i in made[0].indices) {
                for (j in made.indices) {
                    if (i != shortLength - eccLength || j >= shortBlocks) {
                        out[k++] = made[j][i]
                    }
                }
            }
            return out
        }

        internal fun dataCodewords(version: Int, level: Level): Int =
            rawDataModules(version) / 8 -
                ECC_PER_BLOCK[level.ordinal][version] * BLOCKS[level.ordinal][version]

        /** Every module that isn't a finder, timing line, alignment or label. */
        internal fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val aligns = version / 7 + 2
                result -= (25 * aligns - 10) * aligns - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        // ---- Reed–Solomon over GF(256) ---------------------------------------

        internal fun reedSolomonDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in result.indices) {
                    result[j] = multiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = multiply(root, 0x02)
            }
            return result
        }

        internal fun reedSolomonRemainder(data: ByteArray, divisor: IntArray): ByteArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor multiply(divisor[i], factor)
            }
            return ByteArray(result.size) { result[it].toByte() }
        }

        /** Multiplication in the field the standard uses, reduced by 0x11D. */
        private fun multiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        // ---- Labels -----------------------------------------------------------

        /** The 15 bits that say which level and mask, with their own protection. */
        internal fun formatBits(level: Level, mask: Int): Int {
            val data = (level.bits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            return ((data shl 10) or rem) xor 0x5412
        }

        /** The 18 bits that say which version, from 7 up. */
        internal fun versionBits(version: Int): Int {
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            return (version shl 12) or rem
        }

        /** Where the small alignment squares go, along each axis. */
        internal fun alignmentPositions(version: Int): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
            val result = IntArray(count)
            result[0] = 6
            var pos = version * 4 + 10
            for (i in count - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        // Error correction codewords per block, then how many blocks, by
        // level (L, M) and version. Index 0 is unused so versions read as
        // themselves. Straight from the standard's table.
        private val ECC_PER_BLOCK = arrayOf(
            intArrayOf(
                -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28,
                28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
            ),
            intArrayOf(
                -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
                26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
            ),
        )
        private val BLOCKS = arrayOf(
            intArrayOf(
                -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8,
                8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25,
            ),
            intArrayOf(
                -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
                17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
            ),
        )
    }

    /** A growing run of bits, most significant first. */
    private class Bits {
        private val bits = ArrayList<Boolean>()
        val length get() = bits.size

        fun add(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += ((value ushr i) and 1) == 1
        }

        fun toBytes(): ByteArray = ByteArray(bits.size / 8) { at ->
            var b = 0
            for (i in 0 until 8) b = (b shl 1) or (if (bits[at * 8 + i]) 1 else 0)
            b.toByte()
        }
    }

    /**
     * The square being built, and which parts of it are fixed.
     *
     * The fixed parts — finders, timing, alignment and labels — are kept apart
     * so the data and the mask flow around them rather than over them.
     */
    private class Grid(val size: Int) {
        val modules = Array(size) { BooleanArray(size) }
        private val fixed = Array(size) { BooleanArray(size) }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            fixed[y][x] = true
        }

        fun drawFunctionPatterns(version: Int, level: Level) {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)

            val positions = alignmentPositions(version)
            val last = positions.size - 1
            for (i in positions.indices) {
                for (j in positions.indices) {
                    // The three corners already hold a finder.
                    if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue
                    for (dy in -2..2) for (dx in -2..2) {
                        set(positions[i] + dx, positions[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
                    }
                }
            }

            // Reserved now and written properly once the mask is known.
            drawFormat(level, 0)

            if (version >= 7) {
                val bits = versionBits(version)
                for (i in 0 until 18) {
                    val dark = ((bits ushr i) and 1) == 1
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, dark)
                    set(b, a, dark)
                }
            }
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                set(x, y, distance != 2 && distance != 4)
            }
        }

        fun drawFormat(level: Level, mask: Int) {
            val bits = formatBits(level, mask)
            fun bit(i: Int) = ((bits ushr i) and 1) == 1

            // Beside the top-left finder.
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))

            // And again, split between the other two finders.
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)   // always dark
        }

        /** Lay the bits down in the zigzag of two-column strips the standard uses. */
        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5   // step over the timing column
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!fixed[y][x] && i < data.size * 8) {
                            modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) == 1
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (fixed[y][x]) continue
                val flip = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (flip) modules[y][x] = !modules[y][x]
            }
        }

        /**
         * How hard this square would be to read, by the standard's four rules:
         * long runs of one colour, solid blocks, anything that looks like a
         * finder, and a lopsided balance of dark and light.
         */
        fun penalty(): Int {
            var total = 0

            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) {
                        run++
                    } else {
                        if (run >= 5) total += 3 + (run - 5)
                        run = 1
                    }
                }
                if (run >= 5) total += 3 + (run - 5)

                // 1:1:3:1:1 with four light either side.
                for (i in 0..size - 11) {
                    val window = BooleanArray(11) { get(i + it) }
                    if (window.contentEquals(FINDER_LEFT) || window.contentEquals(FINDER_RIGHT)) total += 40
                }
            }

            for (y in 0 until size) line { modules[y][it] }
            for (x in 0 until size) line { modules[it][x] }

            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) total += 3
            }

            val dark = modules.sumOf { row -> row.count { it } }
            val all = size * size
            val k = (kotlin.math.abs(dark * 20 - all * 10) + all - 1) / all - 1
            total += maxOf(0, k) * 10
            return total
        }

        companion object {
            private val FINDER_LEFT = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
            private val FINDER_RIGHT = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
        }
    }
}
