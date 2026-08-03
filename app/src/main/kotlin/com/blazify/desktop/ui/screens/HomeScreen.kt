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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Net
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.HomeHero
import com.blazify.desktop.ui.Look
import com.blazify.desktop.ui.SkeletonRail
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

@Composable
fun HomeScreen(
    onOpen: (Catalogue.Card) -> Unit,
    onPlayAll: (List<Catalogue.Card>, Int) -> Unit,
) {
    // Held outside the screen, so stepping to Explore and back is the page you
    // left rather than a fresh load.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { HomeState.scroll = listState }
    LaunchedEffect(Unit) { HomeState.ensureLoaded() }
    // Built again when the line comes or goes — offline the page is a different
    // page, and a stale one is a page of things that cannot play.
    LaunchedEffect(Net.online) { HomeState.reactTo(Net.online) }

    // Built again when the line comes or goes — offline the page is a
    // different page, and a stale one is a page of things that cannot play.
    LaunchedEffect(Net.online) { HomeState.reactTo(Net.online) }

    val picks = HomeState.picks
    val shelves = HomeState.shelves
    val mood = HomeState.mood

    // Watching the layout itself rather than a true/false "near the end" flag.
    // That flag goes true and STAYS true, and a flow only emits on change — so
    // it fired once and the feed never grew again. This changes every time the
    // list does, which is what actually drives the loading.
    LaunchedEffect(Unit) {
        snapshotFlow {
            val info = listState.layoutInfo
            Triple(
                info.totalItemsCount,
                info.visibleItemsInfo.lastOrNull()?.index ?: -1,
                listState.canScrollForward,
            )
        }.collect { (total, lastVisible, canScrollDown) ->
            // Two reasons to fetch: you're near the bottom, or there isn't
            // enough here to scroll at all — on a tall window the first pages
            // don't fill the screen, and then nothing would ever trigger.
            if (!canScrollDown || lastVisible >= total - 2) HomeState.extend()
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        // The greeting card's figure stands above the card on purpose, and a
        // list crops at its own edge — so the room it needs has to be part of
        // the list rather than part of the card.
        // Only top and bottom. The sides are padded by each item that wants
        // them, which leaves the hero free to be genuinely full width instead
        // of trying to reach past a margin it was given.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 0.dp, bottom = 92.dp,
        ),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (Look.showGreeting) {
            item {
                HomeHero(
                    onPlay = {
                        val songs = (picks + shelves).filter { it.isSongs }.flatMap { it.cards }
                        if (songs.isNotEmpty()) onPlayAll(songs, 0)
                    },
                    onShuffle = {
                        val songs = (picks + shelves).filter { it.isSongs }.flatMap { it.cards }
                        if (songs.isNotEmpty()) onPlayAll(songs.shuffled(), 0)
                    },
                )
            }
        }
        if (HomeState.moods.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 26.dp)) {
                    MoodChips(HomeState.moods, mood) { picked ->
                        scope.launch { HomeState.choose(picked) }
                    }
                }
            }
        }

        if (mood == null) {
            // The songs hold the top of the screen even before they arrive.
            // Building them takes several requests while the feed takes one, so
            // without this the albums would win the race every time and be the
            // first thing on the page — which is exactly backwards.
            if (HomeState.building && picks.isEmpty()) {
                items(3) { Box(Modifier.padding(horizontal = 26.dp)) { SkeletonRail() } }
            }
            items(picks) { shelf ->
                Box(Modifier.padding(horizontal = 26.dp)) { Shelf(shelf, onOpen, onPlayAll) }
            }
        }

        when {
            HomeState.loading -> items(2) {
                Box(Modifier.padding(horizontal = 26.dp)) { SkeletonRail() }
            }
            HomeState.problem != null -> item {
                Box(Modifier.padding(horizontal = 26.dp)) {
                    Text(HomeState.problem!!, color = Blz.muted, fontSize = 13.sp)
                }
            }
            shelves.isEmpty() -> item {
                Box(Modifier.padding(horizontal = 26.dp)) {
                    Text("Nothing in the feed right now", color = Blz.dim, fontSize = 13.sp)
                }
            }
            // Interleaved rather than tipped in as a block: a run of eight
            // album shelves reads as a shop, so a song shelf is dealt back in
            // between them and the page keeps alternating.
            else -> items(interleave(shelves, picks)) { shelf ->
                Box(Modifier.padding(horizontal = 26.dp)) { Shelf(shelf, onOpen, onPlayAll) }
            }
        }

        if (HomeState.extending) item {
            Box(Modifier.padding(horizontal = 26.dp)) { SkeletonRail(count = 5) }
        }
    }

    // Sits over the feed in the bottom corner, just above the transport strip.
    // Everything on this page is songs, and the one thing you might want that
    // isn't a particular one of them is all of them, in no order.
    val everything = (picks + shelves).filter { it.isSongs }.flatMap { it.cards }
    if (everything.isNotEmpty()) {
        ShuffleButton(
            Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 22.dp),
        ) { onPlayAll(everything.shuffled(), 0) }
    }
    }
}

/**
 * Shuffle everything on the page.
 *
 * Round and filled, so it reads as the one thing you can do to the whole
 * screen rather than as another control belonging to a shelf.
 */
@Composable
private fun ShuffleButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
            .hoverLift(hovered, to = 1.05f)
            .hoverGlow(hovered, source)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Rounded.Shuffle, "Shuffle everything", Modifier.size(20.dp), tint = Blaze.OnAmber)
        Text("Shuffle", color = Blaze.OnAmber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MoodChips(
    moods: List<Catalogue.Mood>,
    selected: Catalogue.Mood?,
    onSelect: (Catalogue.Mood?) -> Unit,
) {
    // Scrolled rather than wrapped: ten of these stacked across the top pushed
    // the music down the page, which is the wrong way round for a screen whose
    // whole job is the music.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(moods) { entry ->
            val on = entry.params == selected?.params
            val name = entry.title
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
                    // Picking the one already on turns it off, which is the
                    // only way back to the unfiltered feed once you've chosen.
                    .clickable { onSelect(if (on) null else entry) }
                    // Slim. These are a filter, not a headline — they were
                    // sitting taller than the mark in the rail, which put the
                    // emphasis in completely the wrong place.
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    name,
                    color = if (on) Blaze.OnAmber else Blz.muted,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Deal the second list back through the first.
 *
 * The catalogue's shelves are albums and playlists almost to a fault, and a
 * dozen of them in a row is a wall of covers. Every third one is swapped for a
 * song shelf that hasn't been used at the top, so the page keeps changing
 * rhythm as you scroll rather than settling into one.
 */
private fun interleave(
    shelves: List<Catalogue.Shelf>,
    songs: List<Catalogue.Shelf>,
): List<Catalogue.Shelf> {
    // The first few song shelves are already at the top of the page; only the
    // ones past that are free to be dealt back in.
    val spare = songs.drop(3).toMutableList()
    if (spare.isEmpty()) return shelves

    val out = mutableListOf<Catalogue.Shelf>()
    shelves.forEachIndexed { at, shelf ->
        out += shelf
        if (at % 3 == 2 && spare.isNotEmpty()) out += spare.removeAt(0)
    }
    return out + spare
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up"
}
