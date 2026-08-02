package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Lyrics
import com.blazify.desktop.data.LyricsSource
import com.blazify.desktop.data.Romanize
import com.blazify.desktop.data.Track
import kotlinx.coroutines.delay

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Whether the words have the whole window.
 *
 * Held apart from the frame so the keyboard can reach it: Escape has to leave
 * this view, and the key handler runs above every screen rather than inside
 * one.
 */
object Theatre {
    var open by mutableStateOf(false)

    /** Returns whether there was anything to leave. */
    fun leave(): Boolean {
        if (!open) return false
        open = false
        return true
    }
}

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
    onExpand: () -> Unit,
) {
    val preference = track?.id?.let { LyricsSource.preferenceFor(it) }
    var lyrics by remember(track?.id) { mutableStateOf<Lyrics?>(null) }

    LaunchedEffect(track?.id, preference) {
        lyrics = null
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
                    // Named, because with several sources in play "these words
                    // are wrong" is answerable — you know which one to move
                    // down the list.
                    val credit = LyricsSource.creditFor(it.id)
                    Text(
                        if (credit != null) "${it.title} · $credit" else it.title,
                        color = Blz.dim, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            track?.let { SourceButton(it) }
            RoundIcon(Icons.Rounded.OpenInFull, "Full screen", onExpand)
            RoundIcon(Icons.Rounded.Close, "Close", onClose)
        }

        Body(track, lyrics, position, onSeekTo, scale = 1f, padding = PaddingValues(0.dp))
    }
}

/**
 * The words, and nothing else.
 *
 * The rail, the browser and the transport all go, the type roughly doubles and
 * the sheet centres itself in the window. This is the same panel at a different
 * distance — for a lyric sheet propped up across a room rather than read at
 * arm's length — which is why it shares its rendering rather than being a
 * second screen that will drift out of step with the first.
 */
@Composable
fun LyricsTheatre(
    track: Track?,
    position: Double,
    onSeekTo: (Double) -> Unit,
    onClose: () -> Unit,
) {
    val preference = track?.id?.let { LyricsSource.preferenceFor(it) }
    var lyrics by remember(track?.id) { mutableStateOf<Lyrics?>(null) }

    LaunchedEffect(track?.id, preference) {
        lyrics = null
        lyrics = track?.let { LyricsSource.of(it) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Blz.page)
            // The song's own colour, top and bottom, so a full window of words
            // still belongs to the record rather than to the application.
            .background(
                Brush.verticalGradient(
                    listOf(
                        Blaze.Amber.copy(alpha = 0.13f),
                        Color.Transparent,
                        Blaze.Ember.copy(alpha = 0.09f),
                    ),
                ),
            ),
    ) {
        // Scaled with the window rather than fixed: the point of this view is
        // reading from further away, and a size chosen for a laptop is a
        // whisper on a television.
        val scale = (maxWidth.value / 900f).coerceIn(1.4f, 2.6f)
        val tall = maxHeight

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    track?.let {
                        Text(
                            it.title, color = Blz.ink, fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            it.artist, color = Blz.muted, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                track?.let { SourceButton(it) }
                RoundIcon(Icons.Rounded.CloseFullscreen, "Leave full screen", onClose)
            }

            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                    Body(
                        track, lyrics, position, onSeekTo,
                        scale = scale,
                        // Room above and below so the sung line can sit a third
                        // of the way down without the first and last lines of
                        // the song being stuck against an edge.
                        padding = PaddingValues(horizontal = 34.dp, vertical = tall * 0.22f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Body(
    track: Track?,
    lyrics: Lyrics?,
    position: Double,
    onSeekTo: (Double) -> Unit,
    scale: Float,
    padding: PaddingValues,
) {
    when {
        track == null -> Note("Play something to see its words", scale)
        lyrics == null -> Note("Looking…", scale)
        lyrics.synced -> Synced(lyrics, position, onSeekTo, scale, padding)
        !lyrics.plain.isNullOrBlank() -> Plain(lyrics.plain, scale, padding)
        else -> Note("No lyrics found for this one", scale)
    }
}

@Composable
private fun Synced(
    lyrics: Lyrics,
    position: Double,
    onSeekTo: (Double) -> Unit,
    scale: Float,
    padding: PaddingValues,
) {
    val state = rememberLazyListState()
    // Read a little ahead of where the player says it is, so a line lights up
    // as it starts rather than once it's over.
    val current = lyrics.lineAt(position + Look.lyricsLead)

    // Whether the sheet is still following the song.
    //
    // Reading ahead to see what's coming is a normal thing to want, and a sheet
    // that yanks itself back a second later makes it impossible. So the first
    // touch of the scroll wheel stops it following and says so — and getting
    // back is one click rather than a wait.
    var following by remember(lyrics) { mutableStateOf(true) }
    var driving by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (moving && !driving) following = false
        }
    }

    // Kept a third of the way down rather than centred: the line being sung
    // matters less than the two coming after it, and reading downward wants
    // room ahead of the eye, not behind it.
    LaunchedEffect(current, following) {
        if (current < 0 || !Look.lyricsFollow || !following) return@LaunchedEffect
        val viewport = state.layoutInfo.viewportSize.height
        driving = true
        state.animateScrollToItem(current, scrollOffset = -(viewport / 3))
        // The list settles a frame or two after the animation returns, and
        // letting go of the flag too early reads that settling as a person
        // scrolling — which would switch following off the instant it was
        // switched on.
        delay(120)
        driving = false
    }

    val align = when (Look.lyricsAlign) {
        LyricsAlign.Left -> TextAlign.Start
        LyricsAlign.Centre -> TextAlign.Center
        LyricsAlign.Right -> TextAlign.End
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy((Look.lyricsSpacing * scale).dp),
    ) {
        itemsIndexed(lyrics.lines) { at, line ->
            val active = at == current
            // The sung line takes the colour of the cover, and the rest stay
            // grey. Brightness alone says "this one is legible"; the record's
            // own colour says "this one is now", and it changes with the song
            // rather than being one fixed highlight for everything.
            val colour by animateColorAsState(
                if (active) Blaze.Amber else Blz.dim, tween(180), label = "lyricInk",
            )
            val weight by animateFloatAsState(
                if (active) 1f else 0f, tween(180), label = "lyricWeight",
            )
            val (source, hovered) = rememberHovered()

            val base = Look.lyricsPoints * scale
            Text(
                // Turned into the Latin alphabet when asked, and left alone
                // when there's nothing to turn.
                (if (Look.romanize) Romanize.of(line.text, Look.romanized) else line.text).ifBlank { "·" },
                color = colour,
                // The line being sung is the whole point of the panel, so it
                // steps up in size as well as in colour — dimming alone reads
                // as "these are off" rather than "this one is now".
                fontSize = (if (active) base * 1.12f else base).sp,
                fontWeight = if (weight > 0.5f) FontWeight.Bold else FontWeight.Medium,
                lineHeight = (base * Look.lyricsLineHeight).sp,
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
                    .then(
                        if (Look.lyricsTap) {
                            Modifier.hoverBackground(Blz.hover, hovered, source)
                                .clickable { onSeekTo(line.at) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = (7 * scale).dp),
            )
        }
    }

        // Only while it has been left behind, and over the words rather than
        // beside them — a permanent button for a temporary state is clutter
        // for everyone who never scrolled.
        androidx.compose.animation.AnimatedVisibility(
            visible = !following && Look.lyricsFollow,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        ) {
            ResyncPill { following = true }
        }
    }
}

/**
 * Back to the line being sung.
 *
 * Says which way it will move rather than naming a mode: "sync" is a word about
 * the machinery, and the thing anyone wants here is to be looking at the words
 * that are happening.
 */
@Composable
private fun ResyncPill(onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
            .hoverGlow(hovered, source)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.Rounded.VerticalAlignCenter, null, Modifier.size(16.dp), tint = Blaze.OnAmber)
        Text(
            "Back to the song", color = Blaze.OnAmber, fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Where these particular words came from, and where else to look.
 *
 * The list is every source that's switched on, so when a set of lyrics is for
 * the wrong take of a song the fix is here rather than three screens away in
 * the settings — and it applies to this song only, which is the scope the
 * problem actually has.
 */
@Composable
private fun SourceButton(track: Track) {
    var open by remember { mutableStateOf(false) }
    val chain = Look.lyricsChain()
    val chosen = LyricsSource.preferenceFor(track.id)
    val credit = LyricsSource.creditFor(track.id)

    Box {
        RoundIcon(Icons.Rounded.Translate, "Lyrics source") { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(240.dp).background(Blz.bar),
        ) {
            MenuLine("LYRICS FROM")
            MenuChoice(
                label = "Whichever has them",
                detail = credit?.takeIf { chosen == null }?.let { "Using $it" },
                on = chosen == null,
            ) {
                LyricsSource.prefer(track.id, null)
                open = false
            }
            chain.forEach { provider ->
                MenuChoice(provider.name, null, chosen == provider.name) {
                    LyricsSource.prefer(track.id, provider.name)
                    open = false
                }
            }
            if (chain.size < 2) {
                MenuLine("Turn more on in Settings › Lyrics")
            }
        }
    }
}

@Composable
private fun MenuLine(text: String) {
    Text(
        text, color = Blz.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun MenuChoice(label: String, detail: String?, on: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (on) Blaze.Amber else Blz.ink, fontSize = 13.sp)
            detail?.let { Text(it, color = Blz.dim, fontSize = 11.sp) }
        }
        if (on) Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = Blaze.Amber)
    }
}

@Composable
private fun Plain(text: String, scale: Float, padding: PaddingValues) {
    val align = when (Look.lyricsAlign) {
        LyricsAlign.Left -> TextAlign.Start
        LyricsAlign.Centre -> TextAlign.Center
        LyricsAlign.Right -> TextAlign.End
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(text.lines()) { line ->
            val base = Look.lyricsPoints * scale
            Text(
                (if (Look.romanize) Romanize.of(line, Look.romanized) else line).ifBlank { " " },
                color = Blz.muted,
                fontSize = base.sp,
                lineHeight = (base * Look.lyricsLineHeight).sp,
                textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun Note(text: String, scale: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            text, color = Blz.dim, fontSize = (15 * scale.coerceAtMost(1.6f)).sp,
            modifier = Modifier.padding(top = (10 * scale).dp),
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = Blz.muted)
    }
}
