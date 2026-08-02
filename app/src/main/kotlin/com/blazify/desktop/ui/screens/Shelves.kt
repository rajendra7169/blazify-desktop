package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as itemsIndexedInRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.asTrack
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.Look
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * How big a shelf draws itself.
 *
 * A window is a good deal wider than a hand, so everything here runs larger
 * than it would on a small screen — but the proportions between artwork, title
 * and subtitle are what make a shelf readable, and those are kept.
 */
/** Taken from the setting rather than fixed, so shelves resize with it. */
private val artSide: androidx.compose.ui.unit.Dp
    @Composable get() = Look.gridSize.art.dp
private val TileGap = 14.dp
private val LineHeight = 62.dp
private val LineGap = 12.dp

/**
 * What a song line would like to be, before the pane has its say.
 *
 * Columns are stretched to share the full width rather than held at a fixed
 * size — a fixed one stops short of the edge and leaves a gutter that reads as
 * a mistake. This only decides how many of them there are.
 */
private val LineTarget = 420.dp

/**
 * A shelf, drawn the way the catalogue asked for it.
 *
 * A feed of identical squares is hard to read — everything looks equally
 * important and there's no rhythm to scrolling it. Shelves that stack several
 * deep become a grid of compact lines, because a song there is a line of text
 * and a thumbnail rather than a cover worth showing off. Everything else gets
 * the space its artwork deserves: widescreen stills stay wide, artists are
 * circles, and heights stay level across a rail so a mixed shelf still reads as
 * one row.
 */
@Composable
fun Shelf(
    shelf: Catalogue.Shelf,
    onOpen: (Catalogue.Card) -> Unit,
    onPlayAll: (List<Catalogue.Card>, Int) -> Unit,
) {
    var width by remember { mutableStateOf(0) }

    Column(
        Modifier.fillMaxWidth().onSizeChanged { width = it.width },
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (shelf.isSongs) {
            val state = rememberLazyGridState()
            ShelfHeader(shelf, state, width, onPlay = { onPlayAll(shelf.cards, 0) })
            SongGrid(shelf, state, onPlayAll)
        } else {
            val state = rememberLazyListState()
            ShelfHeader(shelf, state, width, onPlay = null)
            CardRail(shelf, state, onOpen)
        }
    }
}

/**
 * The line above a shelf: what it is, who it came from, and the way through it.
 *
 * The arrows exist because a rail on a desktop has no thumb to flick it — a
 * scroll wheel moves the page, not the row, so without them the far end of a
 * shelf is unreachable for anyone not dragging a scrollbar.
 */
@Composable
private fun ShelfHeader(
    shelf: Catalogue.Shelf,
    state: ScrollableState,
    width: Int,
    onPlay: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    // Just under a full pane, so the tile you were looking at stays in sight
    // and gives you your bearings after the jump.
    fun nudge(direction: Int) = scope.launch {
        state.animateScrollBy(direction * (width * 0.8f).coerceAtLeast(200f))
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        shelf.avatar?.let {
            Artwork(it, size = 42.dp, corner = 21.dp, modifier = Modifier.clip(CircleShape))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            shelf.label?.let {
                Text(
                    it.uppercase(), color = Blz.muted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                shelf.title, color = Blz.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onPlay?.let { PlayAllPill(it) }
            Arrow(Icons.Rounded.ChevronLeft, state.canScrollBackward) { nudge(-1) }
            Arrow(Icons.Rounded.ChevronRight, state.canScrollForward) { nudge(1) }
        }
    }
}

@Composable
private fun PlayAllPill(onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
            .hoverGlow(hovered, source)
            .clickable(onClick = onPlay)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(17.dp), tint = Blaze.OnAmber)
        Text("Play all", color = Blaze.OnAmber, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Arrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Blz.surface)
            .then(if (enabled) Modifier.hoverBackground(Blz.hover, hovered, source) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Left in place while it does nothing, rather than shifting the other
        // arrow sideways every time a rail reaches an end.
        Icon(icon, null, Modifier.size(19.dp), tint = if (enabled) Blz.ink else Blz.dim)
    }
}

/** A grid of compact song lines, as many rows deep as the shelf asked for. */
@Composable
private fun SongGrid(
    shelf: Catalogue.Shelf,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    onPlayAll: (List<Catalogue.Card>, Int) -> Unit,
) {
    val rows = shelf.rows.coerceIn(1, 4)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + LineGap) / (LineTarget + LineGap)).toInt().coerceAtLeast(1)
        val lineWidth = (maxWidth - LineGap * (columns - 1)) / columns

        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(LineHeight * rows + LineGap * (rows - 1)),
            horizontalArrangement = Arrangement.spacedBy(LineGap),
            verticalArrangement = Arrangement.spacedBy(LineGap),
        ) {
            itemsIndexed(shelf.cards, key = { at, card -> "$at-${card.kind.name}-${card.id}" }) { at, card ->
                SongMenu(card.asTrack()) {
                    SongLine(card, lineWidth) { onPlayAll(shelf.cards, at) }
                }
            }
        }
    }
}

@Composable
private fun SongLine(card: Catalogue.Card, width: androidx.compose.ui.unit.Dp, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .width(width)
            .height(LineHeight)
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(card.thumbnail, size = 46.dp)
            if (hovered.value) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(6.dp))
                        .background(Blaze.OnAmber.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp), tint = Blz.ink)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card.title, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.subtitle, color = Blz.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.duration.isNotEmpty()) {
            Text(card.duration, color = Blz.dim, fontSize = 12.sp)
        }
    }
}

/** Artwork cards: square for a release, wide for a video, circular for a person. */
@Composable
private fun CardRail(
    shelf: Catalogue.Shelf,
    state: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (Catalogue.Card) -> Unit,
) {
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        itemsIndexedInRow(
            shelf.cards,
            key = { at: Int, card: Catalogue.Card -> "$at-${card.kind.name}-${card.id}" },
        ) { _, card ->
            Tile(card, onOpen)
        }
    }
}

/**
 * The six colours playlist cards cycle through.
 *
 * A playlist has no artwork of its own worth the name — the catalogue sends a
 * collage or a stock tile — so the phone gives each one a colour instead, and
 * the same six in the same order mean a playlist looks the same in both places.
 */
private val PlaylistColours = listOf(
    Color(0xFFB71C5A), Color(0xFF00838F), Color(0xFF283593),
    Color(0xFF8D6E63), Color(0xFF6A1B9A), Color(0xFFEF6C00),
)

@Composable
private fun Tile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    val round = card.kind == Catalogue.Kind.Artist
    // Heights stay level down the rail; only widescreen stills run wider, so a
    // shelf holding both still reads as a single row rather than a staircase.
    val side = artSide
    val artWidth = if (card.wide && !round) side * 16f / 9f else side

    Column(
        Modifier
            .width(artWidth + 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (card.kind == Catalogue.Kind.Playlist) {
            // Artwork washed into a colour of its own rather than shown plain,
            // so a rail of playlists reads as a set rather than as a jumble of
            // whatever pictures the catalogue happened to send.
            val seed = PlaylistColours[
                (card.id.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) })
                    % PlaylistColours.size
            ]
            Box(
                Modifier
                    .size(artWidth, side)
                    .hoverLift(hovered)
                    .clip(RoundedCornerShape(12.dp))
                    .background(seed),
            ) {
                Artwork(card.thumbnail, size = artWidth, height = side, corner = 0.dp)
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            listOf(
                                seed.copy(alpha = 0.10f),
                                seed.copy(alpha = 0.55f),
                                seed.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
                )
                Text(
                    card.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }
        } else {
            Artwork(
                card.thumbnail,
                size = artWidth,
                height = side,
                corner = if (round) side / 2 else 12.dp,
                modifier = Modifier.hoverLift(hovered)
                    .then(if (round) Modifier.clip(CircleShape) else Modifier),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                card.title, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
            )
            Text(
                card.subtitle, color = Blz.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}
