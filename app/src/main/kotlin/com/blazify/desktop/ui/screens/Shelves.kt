package com.blazify.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A shelf, drawn according to what's on it.
 *
 * A feed of identical squares is hard to read — everything looks equally
 * important and there's no rhythm to scrolling it. Songs go into a compact grid
 * that scrolls sideways, four to a column, because a song is a line of text and
 * a thumbnail rather than a cover worth showing off. Albums and playlists get
 * the space their artwork deserves. Artists are circles, which is how everyone
 * expects a person to be drawn.
 */
@Composable
fun Shelf(shelf: Catalogue.Shelf, onOpen: (Catalogue.Card) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(shelf.title, color = Blz.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (shelf.isSongs) SongGrid(shelf, onOpen) else CardRail(shelf, onOpen)
    }
}

/** Four rows of compact song lines, scrolling sideways a column at a time. */
@Composable
private fun SongGrid(shelf: Catalogue.Shelf, onOpen: (Catalogue.Card) -> Unit) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().height(56.dp * 4 + 8.dp * 3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(shelf.cards, key = { it.kind.name + it.id }) { card -> SongLine(card, onOpen) }
    }
}

@Composable
private fun SongLine(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            // Wide enough for a real title, narrow enough that a second column
            // is always visible — otherwise it reads as a list, not a grid.
            .width(330.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Artwork(card.thumbnail, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                card.title, color = Blz.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.subtitle, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Artwork cards: square for a release, circular for a person. */
@Composable
private fun CardRail(shelf: Catalogue.Shelf, onOpen: (Catalogue.Card) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(shelf.cards, key = { it.kind.name + it.id }) { card -> Tile(card, onOpen) }
    }
}

@Composable
private fun Tile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    val round = card.kind == Catalogue.Kind.Artist

    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Artwork(
            card.thumbnail,
            size = 136.dp,
            corner = if (round) 68.dp else 9.dp,
            modifier = Modifier.hoverLift(hovered).then(if (round) Modifier.clip(CircleShape) else Modifier),
        )
        Column(horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                card.title, color = Blz.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
            )
            Text(
                card.subtitle, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}
