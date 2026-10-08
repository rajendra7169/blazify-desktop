package com.blazify.desktop.audio

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * How much of a frame is black band rather than picture.
 *
 * A great many music videos were cut for the cinema and then delivered in a
 * television frame, so the picture arrives with black bars burnt into it above
 * and below. Cropped to fill a wide box those bars are the first thing you see,
 * and they make the video look like it has been put in the wrong place.
 *
 * The answer is a zoom by just enough to push the bars out of the box, and it
 * has to be decided before the picture is shown: a video that appears and then
 * jumps larger a second later looks broken, where one that simply appears at
 * the right size looks deliberate.
 */
object Letterbox {

    /** Below this a pixel counts as black. Not zero: compression leaves noise. */
    private const val DARK = 24

    /**
     * A band row may carry this much printed matter and still be a band.
     *
     * Credits, a channel's mark and the odd subtitle are printed into the bars
     * often enough that demanding a perfectly empty row would find no bars at
     * all on exactly the videos that have them.
     */
    private const val PRINTED = 0.25

    /** Thinner than this is not worth zooming for. */
    private const val LEAST = 0.04

    /**
     * Thicker than this is not a band, it is a dark scene.
     *
     * A night shot or a fade through black is dark from top to bottom, and
     * reading it as a band would zoom the picture halfway to nothing.
     */
    private const val MOST = 0.22

    /**
     * The share of the height taken by one band, in a single frame.
     *
     * Zero when there is none worth acting on; null when the frame is too dark
     * to say, so a caller sampling several frames can leave it out rather than
     * let a fade to black decide the answer.
     *
     * The pixels are four bytes each, blue-green-red then a byte nobody uses,
     * which is how the video player hands them over.
     */
    fun share(pixels: ByteArray, width: Int, height: Int): Double? {
        if (width <= 0 || height <= 0 || pixels.size < width * height * 4) return null

        // Every fourth pixel across is plenty to tell a band from a picture,
        // and it keeps this cheap enough to run inside the frame callback.
        val step = 4
        val across = (width + step - 1) / step

        fun dark(row: Int): Boolean {
            var printed = 0
            val start = row * width * 4
            var x = 0
            while (x < width) {
                val i = start + x * 4
                val b = pixels[i].toInt() and 0xFF
                val g = pixels[i + 1].toInt() and 0xFF
                val r = pixels[i + 2].toInt() and 0xFF
                if ((r * 299 + g * 587 + b * 114) / 1000 >= DARK) printed++
                x += step
            }
            return printed <= across * PRINTED
        }

        var top = 0
        while (top < height / 2 && dark(top)) top++
        var bottom = 0
        while (bottom < height / 2 && dark(height - 1 - bottom)) bottom++

        // The thinner of the two: the zoom is applied evenly about the middle,
        // so the band that decides it is the one that would show first. A dark
        // strip on one side only is part of the picture, not a band.
        val band = minOf(top, bottom).toDouble() / height
        return when {
            band > MOST -> null
            band < LEAST -> 0.0
            else -> band
        }
    }

    /**
     * The part of the frame that is picture, as a band share trimmed off the
     * top and the bottom.
     *
     * Filling a box from this rather than from the whole frame is the zoom of
     * 1 / (1 − 2 × share) when the box is the frame's own shape, and no zoom at
     * all when the box is so wide that filling it already pushes the bands out
     * — enlarging twice for the same bands would throw away picture for nothing.
     */
    fun trim(share: Double, height: Int): Int = (share.coerceIn(0.0, MOST) * height).toInt()
}
