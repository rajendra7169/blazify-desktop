package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.net.URI
import javax.imageio.ImageIO

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The colour of whatever is playing.
 *
 * A cover is the one thing on screen that already has an identity, and taking
 * the accent from it makes the whole window belong to the song rather than to
 * the application. Off by default, because it is a strong effect and someone
 * who chose an accent meant it.
 *
 * The picture is read at a fraction of its size — a colour is an average, and
 * averaging a thousand pixels gives the same answer as a million for a great
 * deal less work.
 */
object ArtworkColour {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What the current artwork came out as, or null before one has been read. */
    var accent by mutableStateOf<Accent?>(null)
        private set

    private var lastUrl: String? = null
    private val cache = mutableMapOf<String, Accent>()

    /**
     * Read the colour out of an artwork URL.
     *
     * Asking twice for the same picture costs nothing: the answer can't change,
     * and skipping back and forth through a queue would otherwise re-read the
     * same few covers over and over.
     */
    fun follow(url: String?) {
        if (url == lastUrl) return
        lastUrl = url

        if (url == null) {
            accent = null
            return
        }
        cache[url]?.let { accent = it; return }

        scope.launch {
            val found = runCatching { extract(url) }.getOrNull()
            // Another track may have started while this was being read, and its
            // colour should win over one for a cover nobody is looking at.
            if (lastUrl != url) return@launch
            if (found != null) {
                cache[url] = found
                accent = found
            }
        }
    }

    /**
     * The most colourful thing in the picture, rather than the most common.
     *
     * The commonest colour in a cover is usually its background, which is
     * usually grey or black — technically dominant and useless as an accent.
     * Weighting by how saturated a pixel is finds the colour someone would
     * point at if asked what colour the cover is.
     */
    private fun extract(url: String): Accent? {
        val image = ImageIO.read(URI(url.atSize(160)).toURL()) ?: return null
        val small = scaled(image, 48)

        var bestScore = 0f
        var bestRgb = 0
        val hsb = FloatArray(3)

        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val rgb = small.getRGB(x, y)
                java.awt.Color.RGBtoHSB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, hsb)
                val (_, saturation, brightness) = hsb
                // Near-black and near-white carry no hue worth taking, and a
                // washed-out pixel would give a muddy accent.
                if (brightness < 0.25f || brightness > 0.97f || saturation < 0.25f) continue
                val score = saturation * saturation * brightness
                if (score > bestScore) {
                    bestScore = score
                    bestRgb = rgb
                }
            }
        }

        if (bestScore == 0f) return null

        java.awt.Color.RGBtoHSB((bestRgb shr 16) and 0xFF, (bestRgb shr 8) and 0xFF, bestRgb and 0xFF, hsb)
        // Lifted to a usable range whatever the cover: an accent has to carry
        // white or black text on it, and read against both page colours.
        val hue = hsb[0]
        val head = java.awt.Color.HSBtoRGB(hue, hsb[1].coerceIn(0.45f, 0.85f), hsb[2].coerceIn(0.62f, 0.92f))
        // The second stop is the same colour walked around the wheel a little
        // and darkened, which is what makes a gradient rather than a fade.
        val tail = java.awt.Color.HSBtoRGB(
            (hue + 0.04f) % 1f,
            hsb[1].coerceIn(0.55f, 0.95f),
            (hsb[2] * 0.78f).coerceIn(0.45f, 0.78f),
        )

        return Accent(
            label = "From the artwork",
            head = 0xFF000000L or (head.toLong() and 0xFFFFFF),
            tail = 0xFF000000L or (tail.toLong() and 0xFFFFFF),
        )
    }

    private fun scaled(source: BufferedImage, side: Int): BufferedImage {
        val out = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.drawImage(source.getScaledInstance(side, side, java.awt.Image.SCALE_FAST), 0, 0, null)
        g.dispose()
        return out
    }

    /** Ask the catalogue for a small copy rather than a full-size one. */
    private fun String.atSize(pixels: Int) =
        Regex("=w\\d+-h\\d+").replace(this, "=w$pixels-h$pixels")
}

/**
 * An accent that isn't one of the fixed ones.
 *
 * The set on offer is an enum because those are the choices; a colour pulled
 * out of a picture is a colour, so it needs its own shape rather than a
 * pretend entry in the list.
 */
class Accent(val label: String, val head: Long, val tail: Long) {
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

    // Two accents are the same when they'd paint the same, which is what the
    // swatches need to know to show which one is on.
    override fun equals(other: Any?) = other is Accent && other.head == head && other.tail == tail
    override fun hashCode() = (head * 31 + tail).hashCode()

    companion object {
        val Blaze = Accent("Blaze", 0xFFFFA726, 0xFFFF7043)
        val Ocean = Accent("Ocean", 0xFF29B6F6, 0xFF3F51B5)
        val Forest = Accent("Forest", 0xFF66BB6A, 0xFF00897B)
        val Rose = Accent("Rose", 0xFFF06292, 0xFFD81B60)
        val Violet = Accent("Violet", 0xFFAB47BC, 0xFF6A1B9A)
        val Sand = Accent("Sand", 0xFFD7CCC8, 0xFF8D6E63)

        /** The ones you can pick, in the order they're offered. */
        val offered = listOf(Blaze, Ocean, Forest, Rose, Violet, Sand)

        fun named(label: String) = offered.firstOrNull { it.label == label } ?: Blaze
    }
}
