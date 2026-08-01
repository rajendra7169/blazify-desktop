package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.ui.screens.ExploreScreen
import com.blazify.desktop.ui.screens.HomeScreen

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The frame every screen sits in: rail on the left, content in the middle,
 * transport across the bottom. Only the middle ever changes, which is what
 * makes moving around feel like staying in one place rather than jumping
 * between pages.
 */
@Composable
fun AppShell() {
    var destination by remember { mutableStateOf(Destination.Home) }
    var railCollapsed by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Blz.page)) {
        Row(Modifier.weight(1f)) {
            Sidebar(
                current = destination,
                collapsed = railCollapsed,
                onSelect = { destination = it },
                onOpenSettings = { },
            )

            Box(Modifier.weight(1f).fillMaxSize()) {
                Content(destination)
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Blz.line))

        PlayerBar(
            now = PlayerState.current?.let {
                NowPlaying(
                    title = it.title,
                    artist = it.artist,
                    artwork = it.thumbnail,
                    position = PlayerState.progress,
                    elapsed = PlayerState.elapsed,
                    duration = PlayerState.total,
                    playing = PlayerState.playing,
                )
            },
            volume = PlayerState.volume,
            onPlayPause = PlayerState::toggle,
            onSeek = PlayerState::seek,
            onVolume = PlayerState::changeVolume,
            onNext = PlayerState::next,
            onPrevious = PlayerState::previous,
            onToggleLyrics = { lyricsOpen = !lyricsOpen },
            onToggleQueue = { queueOpen = !queueOpen },
            lyricsOpen = lyricsOpen,
            queueOpen = queueOpen,
        )
    }
}

@Composable
private fun Content(destination: Destination) {
    when (destination) {
        Destination.Home -> HomeScreen(onOpen = { })
        Destination.Explore -> ExploreScreen()
        else -> Placeholder(destination)
    }
}

/** Stands in for the screens not built yet. */
@Composable
private fun Placeholder(destination: Destination) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(destination.label, color = Blz.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("Coming next", color = Blz.muted, fontSize = 13.sp)
    }
}
