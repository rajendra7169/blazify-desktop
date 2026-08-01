package com.blazify.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Track
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
 * Everything you kept.
 *
 * Saved albums and playlists in a grid that grows down the page rather than
 * sideways — this is the one screen where you're looking *for* something, and a
 * rail that hides two thirds of itself is the wrong shape for that.
 */
@Composable
fun LibraryScreen(onOpen: (Catalogue.Card) -> Unit) {
    val saved = Library.saved

    if (saved.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Library", color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Albums and playlists you save will collect here",
                color = Blz.muted, fontSize = 13.sp,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(190.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Library", color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${saved.size} saved", color = Blz.muted, fontSize = 13.sp)
            }
        }
        items(saved, key = { it.kind.name + it.id }) { card -> SavedTile(card, onOpen) }
    }
}

@Composable
private fun SavedTile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    val round = card.kind == Catalogue.Kind.Artist

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Artwork(
            card.thumbnail,
            size = 168.dp,
            corner = if (round) 84.dp else 12.dp,
            modifier = Modifier.hoverLift(hovered)
                .then(if (round) Modifier.clip(CircleShape) else Modifier),
        )
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
