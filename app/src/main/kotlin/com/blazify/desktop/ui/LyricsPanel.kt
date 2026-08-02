package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Lyrics
import com.blazify.desktop.data.Romanize
import com.blazify.desktop.data.LyricsSource
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The words, beside what you were doing.
 *
 * A timed transcript scrolls itself and dims everything but the line being
 * sung; clicking a line jumps playback to it, which is the fastest way back to
 * the part you wanted. A song with only flat text still gets a readable page —
 * it just doesn't move.
 */
@Composable
fun LyricsPanel(
    track: Track?,
    position: Double,
    onSeekTo: (Double) -> Unit,
    onClose: () -> Unit,
) {
    var lyrics by remember(track?.id) { mutableStateOf<Lyrics?>(null) }

    LaunchedEffect(track?.id) {
        lyrics = track?.let { LyricsSource.of(it) }
    }

    Column(
        // Wide enough that a full line of a song rarely wraps, which is what
        // makes a transcript readable rather than a column of fragments.
        Modifier.width(440.dp).fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lyrics", color = Blz.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                track?.let {
                    Text(
                        it.title, color = Blz.dim, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val (source, hovered) = rememberHovered()
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Close", Modifier.size(18.dp), tint = Blz.muted)
            }
        }

        when {
            track == null -> Note("Play something to see its words")
            lyrics == null -> Note("Looking…")
            lyrics!!.synced -> Synced(lyrics!!, position, onSeekTo)
            !lyrics!!.plain.isNullOrBlank() -> Plain(lyrics!!.plain!!)
            else -> Note("No lyrics found for this one")
        }
    }
}

@Composable
private fun Synced(lyrics: Lyrics, position: Double, onSeekTo: (Double) -> Unit) {
    val state = rememberLazyListState()
    // Read a little ahead of where the player says it is, so a line lights up
    // as it starts rather than once it's over.
    val current = lyrics.lineAt(position + Look.lyricsLead)

    // Kept a third of the way down rather than centred: the line being sung
    // matters less than the two coming after it, and reading downward wants
    // room ahead of the eye, not behind it.
    // Measured from the panel rather than fixed, so the sung line stays a
    // third of the way down at any window height.
    LaunchedEffect(current) {
        if (current < 0 || !Look.lyricsFollow) return@LaunchedEffect
        val viewport = state.layoutInfo.viewportSize.height
        state.animateScrollToItem(current, scrollOffset = -(viewport / 3))
    }

    val align = when (Look.lyricsAlign) {
        LyricsAlign.Left -> TextAlign.Start
        LyricsAlign.Centre -> TextAlign.Center
        LyricsAlign.Right -> TextAlign.End
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        verticalArrangement = Arrangement.spacedBy(Look.lyricsSpacing.dp),
    ) {
        itemsIndexed(lyrics.lines) { at, line ->
            val active = at == current
            val colour by animateColorAsState(
                if (active) Blz.ink else Blz.dim, tween(180), label = "lyricInk",
            )
            val weight by animateFloatAsState(
                if (active) 1f else 0f, tween(180), label = "lyricWeight",
            )
            val (source, hovered) = rememberHovered()

            val base = Look.lyricsSize.line
            Text(
                // Turned into the Latin alphabet when asked, and left alone
                // when there's nothing to turn.
                (if (Look.romanize) Romanize.of(line.text, Look.romanized) else line.text).ifBlank { "·" },
                color = colour,
                // The line being sung is the whole point of the panel, so it
                // steps up in size as well as in colour — dimming alone reads
                // as "these are off" rather than "this one is now".
                fontSize = (if (active) base + 4 else base).sp,
                fontWeight = if (weight > 0.5f) FontWeight.Bold else FontWeight.Medium,
                lineHeight = (base + 11).sp,
                textAlign = align,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    // Off unless asked for. The line being sung is already the
                    // brightest and largest thing on the sheet, and a panel of
                    // words with a coloured block sliding down it is busier
                    // than the words are worth.
                    .then(
                        if (active && Look.lyricsGlow) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Blaze.Amber.copy(alpha = 0.16f),
                                        Blaze.Ember.copy(alpha = 0.06f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable { onSeekTo(line.at) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Plain(text: String) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(text.lines()) { line ->
            Text(
                (if (Look.romanize) Romanize.of(line, Look.romanized) else line).ifBlank { " " },
                color = Blz.muted,
                fontSize = Look.lyricsSize.line.sp,
                lineHeight = (Look.lyricsSize.line + 10).sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Text(text, color = Blz.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
    }
}
