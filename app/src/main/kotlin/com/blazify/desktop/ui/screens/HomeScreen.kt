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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SkeletonRail
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

private val Moods = listOf(
    "Relax", "Party", "Gym", "Focus", "Sleep", "Drive", "Romance", "Feel good", "Sad",
)

@Composable
fun HomeScreen() {
    // Stands in for the feed request until the catalogue is wired up. The
    // skeletons are the point: they are what the screen looks like while it
    // waits, and every screen after this one reuses them.
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1400)
        loading = false
    }

    var mood by remember { mutableStateOf(Moods.first()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(greeting(), color = Blz.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Picking up where you left off", color = Blz.muted, fontSize = 13.sp)
        }

        MoodChips(selected = mood, onSelect = { mood = it })

        if (loading) {
            SkeletonRail()
            SkeletonRail()
        } else {
            Shelf("Quick picks")
            Shelf("Keep listening")
        }
    }
}

@Composable
private fun MoodChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Moods.take(6).forEach { name ->
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

@Composable
private fun Shelf(title: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(title, color = Blz.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Nothing here until the catalogue is connected", color = Blz.dim, fontSize = 12.sp)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up"
}
