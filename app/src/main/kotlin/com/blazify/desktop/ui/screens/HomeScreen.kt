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
import com.blazify.desktop.data.Library
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.Look
import com.blazify.desktop.ui.SkeletonRail
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered
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
    var shelves by remember { mutableStateOf<List<Catalogue.Shelf>>(emptyList()) }
    // Songs first, built from what's been played rather than taken from the
    // feed — the feed answers with albums and playlists, which are things to
    // look at rather than things to put on.
    var picks by remember { mutableStateOf<List<Catalogue.Shelf>>(emptyList()) }
    var building by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var extending by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    // Once the catalogue's own shelves are exhausted the feed carries on with
    // seeded ones, so scrolling never hits a dead stop.
    var discovered by remember { mutableStateOf(0) }
    // The moods the catalogue itself offers, and which one is picked. They were
    // decoration before — a fixed list of words that filtered nothing.
    var moods by remember { mutableStateOf<List<Catalogue.Mood>>(emptyList()) }
    var mood by remember { mutableStateOf<Catalogue.Mood?>(null) }
    val listState = rememberLazyListState()

    // Re-fetched when the mood changes: it's a different feed, not a filter we
    // could apply to the one already here.
    // Rebuilt on every visit, and shuffled each time, so the top of the screen
    // is a different twenty songs whenever you come back to it.
    LaunchedEffect(Unit) {
        building = true
        picks = Catalogue.songShelves(Library.history, Library.liked).getOrDefault(emptyList())
        building = false
    }

    LaunchedEffect(mood) {
        loading = true
        problem = null
        shelves = emptyList()
        discovered = 0
        Catalogue.home(mood = mood?.params).fold(
            onSuccess = {
                shelves = it.shelves
                more = it.more
                if (it.moods.isNotEmpty()) moods = it.moods
            },
            onFailure = { problem = "Couldn't reach the catalogue" },
        )
        loading = false
    }


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
            if (extending || loading) return@collect
            // Two reasons to fetch: you're near the bottom, or there isn't
            // enough here to scroll at all — on a tall window the first pages
            // don't fill the screen, and then nothing would ever trigger.
            val wanted = !canScrollDown || lastVisible >= total - 2
            if (!wanted) return@collect

            extending = true
            val token = more
            if (token != null) {
                Catalogue.home(after = token, mood = mood?.params).onSuccess { next ->
                    // Shelves repeat across pages often enough to notice; keeping
                    // them out is cheaper than letting the feed stutter.
                    val seen = shelves.map { it.title }.toSet()
                    shelves = shelves + next.shelves.filter { it.title !in seen }
                    more = next.more
                }
            } else if (discovered < Catalogue.seedCount) {
                val next = discovered
                discovered += 1
                Catalogue.discover(next).onSuccess { shelf ->
                    if (shelf.cards.isNotEmpty()) shelves = shelves + shelf
                }
            }
            extending = false
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 26.dp, end = 26.dp, top = 22.dp, bottom = 92.dp,
        ),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (Look.showGreeting) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(greeting(), color = Blz.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("Picking up where you left off", color = Blz.muted, fontSize = 13.sp)
                }
            }
        }
        if (moods.isNotEmpty()) {
            item { MoodChips(moods, mood) { mood = it } }
        }

        if (mood == null) {
            // The songs hold the top of the screen even before they arrive.
            // Building them takes several requests while the feed takes one, so
            // without this the albums would win the race every time and be the
            // first thing on the page — which is exactly backwards.
            if (building && picks.isEmpty()) items(3) { SkeletonRail() }
            items(picks) { shelf -> Shelf(shelf, onOpen, onPlayAll) }
        }

        when {
            loading -> items(2) { SkeletonRail() }
            problem != null -> item { Text(problem!!, color = Blz.muted, fontSize = 13.sp) }
            shelves.isEmpty() -> item {
                Text("Nothing in the feed right now", color = Blz.dim, fontSize = 13.sp)
            }
            // Interleaved rather than tipped in as a block: a run of eight
            // album shelves reads as a shop, so a song shelf is dealt back in
            // between them and the page keeps alternating.
            else -> items(interleave(shelves, picks)) { shelf -> Shelf(shelf, onOpen, onPlayAll) }
        }

        if (extending) item { SkeletonRail(count = 5) }
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
