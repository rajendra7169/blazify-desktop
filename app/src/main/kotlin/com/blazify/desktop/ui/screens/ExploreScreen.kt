package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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
import com.blazify.desktop.data.Searches
import kotlinx.coroutines.launch
import com.blazify.desktop.data.asTrack
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SkeletonRows
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.SongSheetButton
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
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
    // Kept between visits like the home feed: the browse tab is the same for
    // everyone and doesn't change between one look at it and the next.
    val browse = ExploreState.browse
    var genre by remember { mutableStateOf<Catalogue.Genre?>(null) }
    var genreShelves by remember { mutableStateOf<List<Catalogue.Shelf>>(emptyList()) }
    // Everything first — the mixed answer is the right first guess, and
    // narrowing is a thing you do once you know what you didn't find.
    var scope by remember { mutableStateOf(Catalogue.Scope.Everything) }
    var results by remember { mutableStateOf<List<Catalogue.Card>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var guesses by remember { mutableStateOf<List<String>>(emptyList()) }

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
        // Asked for alongside the search rather than instead of it: the guesses
        // are for the next query, and holding the results back until they
        // arrive would make the box feel slower to buy something nobody asked
        // for yet.
        launch { guesses = Catalogue.suggestions(typed).filterNot { it.equals(typed, true) } }
        Catalogue.search(typed, scope).fold(
            onSuccess = {
                results = it
                message = if (it.isEmpty()) "Nothing matched that" else null
                // Remembered once it found something. A query typed towards is
                // not a query, and keeping the ones that matched nothing would
                // offer somebody their own typing mistakes.
                if (it.isNotEmpty()) Searches.note(typed)
            },
            onFailure = { message = "Couldn't reach the catalogue" },
        )
        searching = false
    }

    // Fetched once and kept: the browse tab is the same for everyone and
    // doesn't change between one visit to this screen and the next.
    LaunchedEffect(Unit) { ExploreState.ensureLoaded() }

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

        // What you looked for before, when there is nothing in the box; what
        // the catalogue thinks you mean, once there is. Never both — they
        // answer the same question at two different moments.
        if (query.trim().length < 2) {
            RecentSearches(Searches.all.map { it.words }, onPick = { query = it }, onForget = Searches::forget)
        } else if (guesses.isNotEmpty()) {
            Guesses(guesses) { query = it }
        }

        if (query.trim().length >= 2) ScopeChips(scope) { scope = it }

        when {
            // Nothing typed: the browse tab, which is what this screen is for
            // when you don't already know what you're after.
            query.trim().length < 2 -> BrowseTab(browse, onOpen) { genre = it }
            searching && results.isEmpty() -> SkeletonRows(count = 8)
            message != null -> Text(message!!, color = Blz.muted, fontSize = 13.sp)
            // The unfiltered answer holds both kinds at once. Songs are rows
            // that play; albums, artists and playlists are tiles that open. It
            // used to pick one shape for the whole list, so half the results
            // did the wrong thing when clicked — an album row tried to play an
            // album id as a song, and a song tile tried to open a page that
            // does not exist.
            scope == Catalogue.Scope.Everything -> {
                val songs = results.filter { it.kind == Catalogue.Kind.Song }
                val places = results.filterNot { it.kind == Catalogue.Kind.Song }

                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(songs, key = { at, card -> "s-$at-${card.id}" }) { at, card ->
                        SongMenu(card.asTrack()) {
                            TrackRow(
                                position = at + 1,
                                card = card,
                                playing = PlayerState.current?.id == card.id,
                                onPlay = { PlayerState.play(songs.map { it.asTrack() }, at) },
                            )
                        }
                    }

                    if (places.isNotEmpty()) {
                        item {
                            Text(
                                "Albums, artists and playlists",
                                color = Blz.dim, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
                            )
                        }
                        // Rows rather than a grid, because a grid inside a list
                        // has to guess its own height and gets it wrong.
                        itemsIndexed(places, key = { at, card -> "p-$at-${card.id}" }) { _, card ->
                            PlaceRow(card, onOpen)
                        }
                    }
                }
            }

            scope == Catalogue.Scope.Songs || scope == Catalogue.Scope.Videos ||
                scope == Catalogue.Scope.Episodes -> {
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
 * Somewhere to go, as a row.
 *
 * The same height as a song row so a mixed list reads as one list, and clearly
 * not a song: round artwork for a person, square for a record, and the kind
 * said in words rather than left to be inferred.
 */
@Composable
private fun PlaceRow(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(
            card.thumbnail,
            size = 44.dp,
            corner = if (card.kind == Catalogue.Kind.Artist) 22.dp else 6.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card.title, color = Blz.ink, fontSize = 13.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    card.kind.name,
                    card.subtitle.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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
@OptIn(ExperimentalComposeUiApi::class)
private fun ScopeChips(selected: Catalogue.Scope, onSelect: (Catalogue.Scope) -> Unit) {
    val strip = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Ten of them do not fit a window, and a scrollbar under a row of words is
    // taller than the words. The wheel moves it sideways instead.
    Row(
        Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val turned = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                val by = if (turned.x != 0f) turned.x else turned.y
                scope.launch { strip.scrollBy(by * 64f) }
            }
            .horizontalScroll(strip),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                    .then(
                        if (on) Modifier.hoverGlow(hovered, source)
                        else Modifier.hoverBackground(Blz.hover, hovered, source),
                    )
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

/**
 * What the catalogue thinks you are typing towards.
 *
 * Chips rather than a list dropping over the page: the answers below are still
 * worth seeing while you decide, and a panel that covers them makes every
 * keystroke a choice between looking and reading.
 */
@Composable
private fun Guesses(words: List<String>, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(words.take(8)) { word ->
            val (source, hovered) = rememberHovered()
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Blz.surface)
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable { onPick(word) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(13.dp), tint = Blz.dim)
                Text(word, color = Blz.ink, fontSize = 12.5.sp, maxLines = 1)
            }
        }
    }
}

/** The handful of things you keep coming back to. */
@Composable
private fun RecentSearches(
    words: List<String>,
    onPick: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    if (words.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "RECENT", color = Blz.dim, fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(words) { word ->
                val (source, hovered) = rememberHovered()
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Blz.surface)
                        .hoverBackground(Blz.hover, hovered, source)
                        .clickable { onPick(word) }
                        .padding(start = 13.dp, end = if (hovered.value) 6.dp else 13.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.History, null, Modifier.size(13.dp), tint = Blz.dim)
                    Text(word, color = Blz.ink, fontSize = 12.5.sp, maxLines = 1)
                    // Only once the pointer is on it: a row of crosses reads as
                    // a list of things to get rid of rather than places to go.
                    if (hovered.value) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onForget(word) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, "Forget", Modifier.size(12.dp), tint = Blz.muted)
                        }
                    }
                }
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
internal fun ResultTile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
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
        SongSheetButton(card.asTrack(), hovered.value)
    }
}
