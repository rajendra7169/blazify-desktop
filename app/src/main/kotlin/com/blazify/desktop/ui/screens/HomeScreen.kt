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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SkeletonRail
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered
import java.time.LocalTime

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

private val Moods = listOf(
    "Relax", "Party", "Gym", "Focus", "Sleep", "Drive", "Romance", "Feel good",
)

@Composable
fun HomeScreen(
    onOpen: (Catalogue.Card) -> Unit,
    onPlayAll: (List<Catalogue.Card>, Int) -> Unit,
) {
    var shelves by remember { mutableStateOf<List<Catalogue.Shelf>>(emptyList()) }
    var more by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var extending by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var mood by remember { mutableStateOf(Moods.first()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        Catalogue.home().fold(
            onSuccess = { shelves = it.shelves; more = it.more },
            onFailure = { problem = "Couldn't reach the catalogue" },
        )
        loading = false
    }

    // Once the catalogue's own shelves are exhausted the feed carries on with
    // seeded ones, so scrolling never hits a dead stop.
    var discovered by remember { mutableStateOf(0) }

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
                Catalogue.home(after = token).onSuccess { next ->
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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(greeting(), color = Blz.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("Picking up where you left off", color = Blz.muted, fontSize = 13.sp)
            }
        }
        item { MoodChips(mood) { mood = it } }

        when {
            loading -> items(2) { SkeletonRail() }
            problem != null -> item { Text(problem!!, color = Blz.muted, fontSize = 13.sp) }
            shelves.isEmpty() -> item {
                Text("Nothing in the feed right now", color = Blz.dim, fontSize = 13.sp)
            }
            else -> items(shelves) { shelf -> Shelf(shelf, onOpen, onPlayAll) }
        }

        if (extending) item { SkeletonRail(count = 5) }
    }
}

@Composable
private fun MoodChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Moods.forEach { name ->
            val on = name == selected
            val (source, hovered) = rememberHovered()
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                        else Brush.linearGradient(listOf(Blz.surface, Blz.surface)),
                    )
                    .then(if (on) Modifier else Modifier.hoverBackground(Blz.hover, hovered, source))
                    .clickable { onSelect(name) }
                    // Slim. These are a filter, not a headline — they were
                    // sitting taller than the mark in the rail, which put the
                    // emphasis in completely the wrong place.
                    .padding(horizontal = 11.dp, vertical = 4.dp),
            ) {
                Text(
                    name,
                    color = if (on) Blaze.OnAmber else Blz.muted,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up"
}
