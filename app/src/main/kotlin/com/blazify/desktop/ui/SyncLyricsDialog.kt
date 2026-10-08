package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Lyrics
import com.blazify.desktop.data.LyricsSource
import com.blazify.desktop.data.Track
import com.blazify.desktop.data.lrcStamp

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/** How far Undo goes back, so the line can be caught again on its way in. */
private const val REWIND = 3.0

/**
 * Timing a song's words yourself, one tap per line.
 *
 * For a song every source has only as flat text. The song plays from the top
 * and the next line waits, lit; a tap — or Space — says "this line starts now".
 * That is the whole of it, which is why it works: anyone who knows the song
 * can keep time with it, and a missed beat is one Undo away, which also backs
 * the song up a few seconds so the line can be caught again.
 *
 * Nothing is written until Save. Closing the dialog, or the song moving on,
 * throws the taps away and the words stay as they were.
 */
@Composable
fun SyncLyricsDialog(track: Track, lyrics: Lyrics, onDismiss: () -> Unit) {
    // Blank lines are the gaps between verses. Asking for a tap on a gap is
    // asking somebody to time silence, so they are left out.
    val lines = remember(lyrics) {
        lyrics.plain.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
    val stamps = remember(track.id) { mutableStateListOf<Double>() }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()

    // A different song playing means every tap from here would be timing the
    // wrong recording, so tapping stops until it's this one again.
    val same = PlayerState.current?.id == track.id
    val next = stamps.size
    val done = next >= lines.size

    fun tap() {
        if (!same || done) return
        // Never earlier than the line before: a tap can't land before one
        // already made, and a sheet with lines out of order can't be followed.
        stamps += maxOf(PlayerState.positionSeconds, stamps.lastOrNull() ?: 0.0)
    }

    fun undo() {
        if (stamps.isEmpty()) return
        stamps.removeAt(stamps.lastIndex)
        if (same) PlayerState.seekTo((PlayerState.positionSeconds - REWIND).coerceAtLeast(0.0))
    }

    fun startOver() {
        stamps.clear()
        if (same) PlayerState.seekTo(0.0)
    }

    fun save() {
        if (stamps.isEmpty()) return
        // Only the lines that were reached. Any left untimed at the end are
        // dropped rather than guessed at — a made-up time is worse than none.
        val lrc = lines.zip(stamps).joinToString("\n") { (text, at) -> lrcStamp(at) + text }
        LyricsSource.keepOwn(track.id, lrc)
        onDismiss()
    }

    // From the top, playing, as soon as the dialog opens: the first line is
    // usually seconds away and there is no time to go looking for the button.
    LaunchedEffect(track.id) {
        if (same) {
            PlayerState.seekTo(0.0)
            if (!PlayerState.playing) PlayerState.toggle()
        }
        focus.requestFocus()
    }

    // The line waiting for a tap stays in view, a couple of lines down so the
    // ones coming after it can be read ahead.
    LaunchedEffect(next) {
        if (lines.isNotEmpty()) list.animateScrollToItem((next - 2).coerceIn(0, lines.lastIndex))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.Scrim)
            // Space is the tap, and the window would otherwise take it as
            // pause. Caught here, on the way down, so it reaches neither the
            // player nor whichever button was clicked last.
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Spacebar -> {
                        if (event.type == KeyEventType.KeyDown) tap()
                        true
                    }
                    Key.Backspace -> {
                        if (event.type == KeyEventType.KeyDown) undo()
                        true
                    }
                    Key.Escape -> {
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("Sync the lyrics", color = Blz.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !same -> "Another song is playing. Play ${track.title} again to carry on."
                        done -> "Every line has a time. Save to keep it."
                        else -> "Tap, or press Space, as each lit line begins. Backspace undoes."
                    },
                    color = Blz.dim, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            LazyColumn(
                Modifier.fillMaxWidth().height(300.dp),
                state = list,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(lines) { at, text ->
                    val waiting = at == next
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (waiting) Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(Blaze.Amber.copy(alpha = 0.18f), Blaze.Ember.copy(alpha = 0.05f)),
                                    ),
                                ) else Modifier,
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stamps.getOrNull(at)?.let { lrcStamp(it).trim('[', ']') } ?: "",
                            color = Blz.dim, fontSize = 11.5.sp,
                            modifier = Modifier.width(62.dp),
                        )
                        Text(
                            text,
                            color = when {
                                waiting -> Blaze.Amber
                                at < next -> Blz.muted
                                else -> Blz.ink
                            },
                            fontSize = if (waiting) 16.sp else 14.sp,
                            fontWeight = if (waiting) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            TapButton(enabled = same && !done, count = "${stamps.size} of ${lines.size}") {
                tap()
                focus.requestFocus()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                DialogAction("Undo", strong = false, enabled = stamps.isNotEmpty()) {
                    undo()
                    focus.requestFocus()
                }
                DialogAction("Start over", strong = false, enabled = stamps.isNotEmpty()) {
                    startOver()
                    focus.requestFocus()
                }
                Box(Modifier.weight(1f))
                DialogAction("Cancel", strong = false, enabled = true, onClick = onDismiss)
                DialogAction("Save", strong = true, enabled = stamps.isNotEmpty(), onClick = ::save)
            }
        }
    }
}

/**
 * The one control that matters, made big.
 *
 * Timing is done with the eyes on the words, so this is a target that can be
 * hit without looking at it.
 */
@Composable
private fun TapButton(enabled: Boolean, count: String, onTap: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (enabled) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
            )
            .then(if (enabled) Modifier.hoverGlow(hovered, source) else Modifier)
            .clickable(enabled = enabled, onClick = onTap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.TouchApp, null,
            tint = if (enabled) Blaze.OnAmber else Blz.dim,
        )
        Text(
            "Tap",
            color = if (enabled) Blaze.OnAmber else Blz.dim,
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            count,
            color = (if (enabled) Blaze.OnAmber else Blz.dim).copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
