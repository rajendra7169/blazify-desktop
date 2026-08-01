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
import androidx.compose.runtime.LaunchedEffect
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

private val Moods = listOf("Relax", "Party", "Gym", "Focus", "Sleep", "Drive")

@Composable
fun HomeScreen(onOpen: (Catalogue.Card) -> Unit) {
    var shelves by remember { mutableStateOf<List<Catalogue.Shelf>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    var mood by remember { mutableStateOf(Moods.first()) }

    LaunchedEffect(Unit) {
        Catalogue.home().fold(
            onSuccess = { shelves = it },
            onFailure = { problem = "Couldn't reach the catalogue" },
        )
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
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
            else -> items(shelves) { shelf -> Shelf(shelf, onOpen) }
        }
    }
}

@Composable
private fun Shelf(shelf: Catalogue.Shelf, onOpen: (Catalogue.Card) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(shelf.title, color = Blz.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(shelf.cards, key = { it.kind.name + it.id }) { card -> Tile(card, onOpen) }
        }
    }
}

@Composable
private fun Tile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(10.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Artwork(card.thumbnail, size = 120.dp, corner = 9.dp, modifier = Modifier.hoverLift(hovered))
        Column {
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

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up"
}
