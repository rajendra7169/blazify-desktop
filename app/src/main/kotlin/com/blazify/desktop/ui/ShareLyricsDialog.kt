package com.blazify.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Lyrics
import com.blazify.desktop.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** More than this and the card stops being a quote and becomes the whole song. */
private const val MOST_LINES = 6

/** The card's width as drawn in the file, in the card's own units. */
private const val CARD_WIDTH = 540

/**
 * A few lines of a song, made into a picture to send somebody.
 *
 * A screenshot of the lyric sheet carries the rest of the window with it and
 * never says what the song is. This makes a card that does: the cover, the
 * name, the lines you picked and nothing else, at a size that survives being
 * passed through a chat app.
 */
@Composable
fun ShareLyricsDialog(track: Track, lyrics: Lyrics, onDismiss: () -> Unit) {
    // Blank lines are the pauses between verses; on a card they are just gaps.
    val all = remember(lyrics) {
        (if (lyrics.synced) lyrics.lines.map { it.text } else lyrics.plain.orEmpty().lines())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    val picked = remember(lyrics) {
        mutableStateListOf<Int>().apply { addAll(all.indices.take(2)) }
    }
    var cover by remember(track.id) { mutableStateOf<ImageBitmap?>(null) }
    var said by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track.id) { cover = withContext(Dispatchers.IO) { coverOf(track) } }

    val chosen = picked.sorted().mapNotNull { all.getOrNull(it) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.Scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(780.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Share as an image", color = Blz.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // The card itself, smaller. The same drawing as the file, so
                // what you see here is exactly what gets sent.
                Box(
                    Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    LyricsCard(track, cover, chosen, unit = 0.62f)
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pick up to $MOST_LINES lines",
                        color = Blz.dim, fontSize = 12.sp,
                    )
                    LazyColumn(Modifier.heightIn(max = 410.dp)) {
                        itemsIndexed(all) { at, text ->
                            val on = at in picked
                            val (source, hovered) = rememberHovered()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .hoverBackground(Blz.hover, hovered, source)
                                    .clickable {
                                        said = null
                                        if (on) picked.remove(at)
                                        else if (picked.size < MOST_LINES) picked.add(at)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (on) Blaze.Amber else Blz.surfaceHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = Blaze.OnAmber)
                                }
                                Text(
                                    text,
                                    color = if (on) Blz.ink else Blz.muted,
                                    fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    said ?: "",
                    color = Blz.dim, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DialogAction("Close", strong = false, enabled = true, onClick = onDismiss)
                DialogAction("Copy image", strong = false, enabled = chosen.isNotEmpty()) {
                    said = if (copyImage(cardImage(track, cover, chosen))) "Copied — paste it anywhere"
                    else "Couldn't reach the clipboard"
                }
                DialogAction("Save image…", strong = true, enabled = chosen.isNotEmpty()) {
                    val target = chooseImageFile(track) ?: return@DialogAction
                    said = if (runCatching { ImageIO.write(cardImage(track, cover, chosen), "png", target) }.getOrDefault(false)) {
                        "Saved to ${target.name}"
                    } else {
                        "Couldn't save it there"
                    }
                }
            }
        }
    }
}

/** A text button in the style of the app's other dialogs. */
@Composable
internal fun DialogAction(label: String, strong: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = when {
            !enabled -> Blz.dim
            strong -> Blaze.Amber
            else -> Blz.muted
        },
        fontSize = 13.sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * The card.
 *
 * Its colours are fixed rather than taken from the light or dark look: it is
 * going to be seen in somebody else's chat, on somebody else's screen, and a
 * picture that changes with the sender's settings is two different pictures.
 * [unit] scales everything together, so the preview and the file are one
 * drawing at two sizes.
 */
@Composable
private fun LyricsCard(track: Track, cover: ImageBitmap?, lines: List<String>, unit: Float, modifier: Modifier = Modifier) {
    val d = { value: Float -> (value * unit).dp }
    val s = { value: Float -> (value * unit).sp }
    Column(
        modifier
            .width(d(CARD_WIDTH.toFloat()))
            .background(Color(0xFF111111))
            .background(
                Brush.linearGradient(
                    listOf(Blaze.Amber.copy(alpha = 0.55f), Blaze.Ember.copy(alpha = 0.22f), Color.Transparent),
                ),
            )
            .padding(d(36f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(d(64f)).clip(RoundedCornerShape(d(10f))).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(d(28f)), tint = Color.White.copy(alpha = 0.6f))
                }
            }
            Column(Modifier.padding(start = d(16f))) {
                Text(
                    track.title, color = Color.White, fontSize = s(20f), fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = s(25f),
                )
                Text(
                    track.artist, color = Color.White.copy(alpha = 0.72f), fontSize = s(15f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(d(30f)))
        Column(verticalArrangement = Arrangement.spacedBy(d(10f))) {
            lines.forEach { line ->
                Text(
                    line, color = Color.White, fontSize = s(25f), fontWeight = FontWeight.Bold,
                    lineHeight = s(33f),
                )
            }
        }
        Spacer(Modifier.size(d(34f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            blazifyMark?.let { Image(it, null, Modifier.size(d(20f))) }
            Text(
                "Blazify", color = Color.White.copy(alpha = 0.66f), fontSize = s(13f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = d(7f)),
            )
        }
    }
}

/**
 * The card as a picture, twice the size it is laid out at so it stays sharp.
 *
 * Drawn off screen into a tall canvas and then cut to the height the card
 * actually took, because how tall it is depends on how the chosen lines wrap
 * and nobody knows that until they have been laid out.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun cardImage(track: Track, cover: ImageBitmap?, lines: List<String>): BufferedImage {
    val density = 2f
    var tall = 0
    val scene = ImageComposeScene(
        width = (CARD_WIDTH * density).toInt(),
        height = 3000,
        density = Density(density),
    ) {
        LyricsCard(track, cover, lines, unit = 1f, modifier = Modifier.onSizeChanged { tall = it.height })
    }
    return try {
        val drawn = scene.render().toComposeImageBitmap().toAwtImage()
        val height = tall.coerceIn(1, drawn.height)
        // Copied out rather than handed back as a view onto the tall one, so
        // the encoder and the clipboard see a picture of exactly this size.
        BufferedImage(drawn.width, height, BufferedImage.TYPE_INT_RGB).also { cut ->
            cut.createGraphics().apply {
                drawImage(drawn.getSubimage(0, 0, drawn.width, height), 0, 0, null)
                dispose()
            }
        }
    } finally {
        scene.close()
    }
}

/**
 * The cover, fetched again at a size fit for a picture.
 *
 * The one on screen is drawn by the image loader and not ours to borrow, and
 * the card is drawn somewhere the loader never runs. A cover that can't be had
 * leaves a note symbol in its place rather than holding up the card.
 */
private fun coverOf(track: Track): ImageBitmap? = runCatching {
    val url = track.thumbnail ?: return null
    val bytes = if (url.startsWith("file:")) {
        File(java.net.URI(url)).readBytes()
    } else {
        val sized = Regex("=w\\d+-h\\d+").replace(url, "=w256-h256")
        (java.net.URI(sized).toURL().openConnection() as java.net.HttpURLConnection).run {
            connectTimeout = 8_000
            readTimeout = 8_000
            inputStream.use { it.readBytes() }
        }
    }
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

/** Onto the clipboard as a picture, so it pastes straight into a chat. */
private fun copyImage(image: BufferedImage): Boolean = runCatching {
    val picture = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(picture, null)
    true
}.getOrDefault(false)

/**
 * The system's own save dialog, suggesting a name from the song.
 *
 * Kept a PNG whatever it is called, so it opens by double-clicking.
 */
private fun chooseImageFile(track: Track): File? {
    val name = "${track.title} lyrics".replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifEmpty { "Lyrics" }
    val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
        dialogTitle = "Save the lyrics as an image"
        selectedFile = File("$name.png")
    }
    if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
    val picked = chooser.selectedFile ?: return null
    return if (picked.name.endsWith(".png", true)) picked else File(picked.parentFile, picked.name + ".png")
}
