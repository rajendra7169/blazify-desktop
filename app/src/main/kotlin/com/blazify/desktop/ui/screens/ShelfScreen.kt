package com.blazify.desktop.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.asTrack
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SkeletonRows
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A whole shelf, rather than the dozen that fitted.
 *
 * An artist's page shows a handful of each thing and, before this, that was
 * where browsing stopped — you could see there were more albums and had no way
 * to reach them. This is the rest, fetched a page at a time as it is scrolled,
 * because an artist with three hundred songs should not arrive all at once.
 */
@Composable
fun ShelfScreen(
    title: String,
    more: Catalogue.More,
    onBack: () -> Unit,
    onOpen: (Catalogue.Card) -> Unit,
) {
    var cards by remember(more) { mutableStateOf<List<Catalogue.Card>>(emptyList()) }
    var next by remember(more) { mutableStateOf<String?>(null) }
    var loading by remember(more) { mutableStateOf(true) }
    var extending by remember(more) { mutableStateOf(false) }
    val grid = rememberLazyGridState()

    LaunchedEffect(more) {
        loading = true
        Catalogue.expand(more).onSuccess { (found, continuation) ->
            cards = found
            next = continuation
        }
        loading = false
    }

    // Fetched as the bottom comes into view rather than on a button, which is
    // what makes a long list feel like one list.
    LaunchedEffect(grid, next) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            val token = next ?: return@collect
            if (extending || last == null) return@collect
            if (last < cards.size - 8) return@collect
            extending = true
            Catalogue.expand(more, token).onSuccess { (found, continuation) ->
                val fresh = found.filterNot { new -> cards.any { it.id == new.id } }
                cards = cards + fresh
                next = continuation
            }
            extending = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 26.dp, top = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val (source, hovered) = rememberHovered()
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(19.dp), tint = Blz.muted)
            }
            Column {
                Text(title, color = Blz.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (loading) "Loading…" else "${cards.size}${if (next != null) "+" else ""} items",
                    color = Blz.muted, fontSize = 12.5.sp,
                )
            }
        }

        if (loading && cards.isEmpty()) {
            SkeletonRows(count = 8)
            return
        }

        // Songs are a list; everything else is a grid of artwork. Which it is
        // follows the contents rather than the heading, because a shelf called
        // "Singles" holds songs and one called "Albums" does not.
        val songs = cards.all { it.kind == Catalogue.Kind.Song }

        LazyVerticalGrid(
            columns = if (songs) GridCells.Fixed(1) else GridCells.Adaptive(180.dp),
            state = grid,
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(if (songs) 0.dp else 14.dp),
        ) {
            itemsIndexed(cards, key = { at, card -> "$at-${card.id}" }) { at, card ->
                if (card.kind == Catalogue.Kind.Song) {
                    SongMenu(card.asTrack()) {
                        SongLine(card, at) {
                            PlayerState.play(
                                cards.filter { it.kind == Catalogue.Kind.Song }.map { it.asTrack() },
                                cards.filter { it.kind == Catalogue.Kind.Song }
                                    .indexOfFirst { it.id == card.id }.coerceAtLeast(0),
                            )
                        }
                    }
                } else {
                    ResultTile(card, onOpen)
                }
            }

            if (extending) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Loading more…", color = Blz.dim, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SongLine(card: Catalogue.Card, at: Int, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(card.thumbnail, size = 42.dp, corner = 6.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card.title,
                color = if (PlayerState.current?.id == card.id) com.blazify.desktop.ui.Blaze.Amber else Blz.ink,
                fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.subtitle, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(card.duration, color = Blz.dim, fontSize = 11.5.sp)
    }
}
