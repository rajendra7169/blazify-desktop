package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.Typing
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.asTrack
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SkeletonRows
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.delay

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Search.
 *
 * What you're looking for decides how it's drawn: songs are a list, because
 * you're picking one to play and a list is quickest to read down. Everything
 * else is a grid of artwork, because you recognise an album by its cover long
 * before you read its name.
 */
@Composable
fun ExploreScreen(
    onOpen: (Catalogue.Card) -> Unit,
    onPlayAll: (List<Catalogue.Card>, Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var browse by remember { mutableStateOf<Catalogue.Explore?>(null) }
    var genre by remember { mutableStateOf<Catalogue.Genre?>(null) }
    var genreShelves by remember { mutableStateOf<List<Catalogue.Shelf>>(emptyList()) }
    var scope by remember { mutableStateOf(Catalogue.Scope.Songs) }
    var results by remember { mutableStateOf<List<Catalogue.Card>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Search as you type, but only once you've stopped. Firing on every
    // keystroke would send a request per letter and show results for a prefix
    // nobody meant to search for. Changing what you're looking for re-runs it
    // straight away — the words haven't changed, so there's nothing to wait for.
    LaunchedEffect(query, scope) {
        val typed = query.trim()
        if (typed.length < 2) {
            results = emptyList(); searching = false; message = null
            return@LaunchedEffect
        }
        searching = true
        message = null
        delay(350)
        Catalogue.search(typed, scope).fold(
            onSuccess = {
                results = it
                message = if (it.isEmpty()) "Nothing matched that" else null
            },
            onFailure = { message = "Couldn't reach the catalogue" },
        )
        searching = false
    }

    // Fetched once and kept: the browse tab is the same for everyone and
    // doesn't change between one visit to this screen and the next.
    LaunchedEffect(Unit) {
        if (browse == null) browse = Catalogue.explore().getOrNull()
    }

    LaunchedEffect(genre) {
        genreShelves = emptyList()
        genre?.let { genreShelves = Catalogue.genre(it).getOrDefault(emptyList()) }
    }

    // A genre takes over the screen: you asked to look at one thing, and the
    // search field above it would only be an invitation to leave.
    genre?.let { picked ->
        GenreScreen(picked, genreShelves, onOpen, onPlayAll) { genre = null }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SearchField(query) { query = it }
        if (query.trim().length >= 2) ScopeChips(scope) { scope = it }

        when {
            // Nothing typed: the browse tab, which is what this screen is for
            // when you don't already know what you're after.
            query.trim().length < 2 -> BrowseTab(browse, onOpen) { genre = it }
            searching && results.isEmpty() -> SkeletonRows(count = 8)
            message != null -> Text(message!!, color = Blz.muted, fontSize = 13.sp)
            scope == Catalogue.Scope.Songs || scope == Catalogue.Scope.Videos -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(results, key = { at, card -> "$at-${card.id}" }) { at, card ->
                        SongMenu(card.asTrack()) {
                        TrackRow(
                            position = at + 1,
                            card = card,
                            playing = PlayerState.current?.id == card.id,
                            onPlay = { PlayerState.play(results.map { it.asTrack() }, at) },
                        )
                        }
                    }
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(
                    results,
                    key = { at: Int, card: Catalogue.Card -> "$at-${card.id}" },
                ) { _, card ->
                    ResultTile(card, onOpen)
                }
            }
        }
    }
}

/**
 * What the catalogue is offering, when you haven't asked for anything.
 *
 * New releases as artwork, then the genre tiles — which are coloured by the
 * catalogue itself, so they're drawn in the colour it sent rather than in ours.
 */
@Composable
private fun BrowseTab(
    browse: Catalogue.Explore?,
    onOpen: (Catalogue.Card) -> Unit,
    onGenre: (Catalogue.Genre) -> Unit,
) {
    if (browse == null) {
        SkeletonRows(count = 6)
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (browse.releases.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "New releases", color = Blz.ink, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(browse.releases, key = { at, card -> "$at-${card.id}" }) { _, card ->
                            Box(Modifier.width(172.dp)) { ResultTile(card, onOpen) }
                        }
                    }
                }
            }
        }

        item {
            Text("Moods and genres", color = Blz.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        items(browse.genres.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { entry ->
                    Box(Modifier.weight(1f)) { GenreTile(entry, onGenre) }
                }
                // Keeps the last row's tiles the same width as every other
                // row's, rather than letting two stretch to fill three places.
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GenreTile(genre: Catalogue.Genre, onOpen: (Catalogue.Genre) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Blz.surface)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(genre) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The stripe is the catalogue's own colour for the genre. Drawing it as
        // an edge rather than a fill keeps the text readable whatever colour
        // arrives, including the ones that fight both themes.
        Box(Modifier.width(5.dp).height(52.dp).background(Color(genre.colour)))
        Text(
            genre.title, color = Blz.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

@Composable
private fun GenreScreen(
    genre: Catalogue.Genre,
    shelves: List<Catalogue.Shelf>,
    onOpen: (Catalogue.Card) -> Unit,
    onPlayAll: (List<Catalogue.Card>, Int) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            val (source, hovered) = rememberHovered()
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(17.dp), tint = Blz.muted)
                Text("Explore", color = Blz.muted, fontSize = 13.sp)
            }
        }
        item {
            Text(genre.title, color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        if (shelves.isEmpty()) {
            item { SkeletonRows(count = 5) }
        } else {
            items(shelves) { shelf -> Shelf(shelf, onOpen, onPlayAll) }
        }
    }
}

@Composable
private fun ScopeChips(selected: Catalogue.Scope, onSelect: (Catalogue.Scope) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Catalogue.Scope.entries.forEach { scope ->
            val on = scope == selected
            val (source, hovered) = rememberHovered()
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                        else Brush.linearGradient(listOf(Blz.surface, Blz.surface)),
                    )
                    .then(if (on) Modifier else Modifier.hoverBackground(Blz.hover, hovered, source))
                    .clickable { onSelect(scope) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    scope.label,
                    color = if (on) Blaze.OnAmber else Blz.muted,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Blz.surface)
            .hoverBackground(Blz.hover, hovered, source)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = Blz.dim)
        Box(Modifier.fillMaxWidth()) {
            if (value.isEmpty()) {
                Text("Search songs, artists, albums", color = Blz.dim, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Blz.ink, fontSize = 13.sp),
                cursorBrush = SolidColor(Blaze.Amber),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { Typing.active = it.isFocused },
            )
        }
    }
}

@Composable
private fun ResultTile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
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
            size = 158.dp,
            corner = if (round) 79.dp else 12.dp,
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

@Composable
private fun TrackRow(position: Int, card: Catalogue.Card, playing: Boolean, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    val accent = if (playing) Blaze.Amber else Blz.ink

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Text(
                if (playing) "♪" else "$position",
                color = if (playing || hovered.value) Blaze.Amber else Blz.dim,
                fontSize = 11.5.sp,
            )
        }
        Artwork(card.thumbnail, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card.title, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
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
