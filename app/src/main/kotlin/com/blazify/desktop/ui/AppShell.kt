package com.blazify.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.ui.screens.CollectionScreen
import com.blazify.desktop.ui.screens.DownloadsScreen
import com.blazify.desktop.ui.screens.ExploreScreen
import com.blazify.desktop.ui.screens.HomeScreen
import com.blazify.desktop.ui.screens.LibraryScreen
import com.blazify.desktop.ui.screens.LocalScreen
import com.blazify.desktop.ui.screens.SettingsScreen
import com.blazify.desktop.ui.screens.TrackListScreen

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
    var railCollapsed by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Blz.page)) {
        Row(Modifier.weight(1f)) {
            Sidebar(
                current = Navigator.destination,
                collapsed = railCollapsed,
                onSelect = Navigator::go,
                onOpenSettings = Navigator::openSettings,
            )

            Box(Modifier.weight(1f).fillMaxSize()) {
                Content(Navigator.destination)
            }

            AnimatedVisibility(
                visible = lyricsOpen,
                enter = expandHorizontally(tween(180)) + fadeIn(tween(180)),
                exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
            ) {
                Row {
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Blz.line))
                    LyricsPanel(
                        track = PlayerState.current,
                        position = PlayerState.positionSeconds,
                        onSeekTo = PlayerState::seekTo,
                        onClose = { lyricsOpen = false },
                    )
                }
            }

            // Slides in beside the content rather than over it, so browsing
            // carries on while it's open.
            AnimatedVisibility(
                visible = queueOpen,
                enter = expandHorizontally(tween(180)) + fadeIn(tween(180)),
                exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
            ) {
                Row {
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Blz.line))
                    QueuePanel(
                        queue = PlayerState.queue,
                        current = PlayerState.index,
                        onJump = PlayerState::jumpTo,
                        onRemove = PlayerState::removeAt,
                    )
                }
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
                    liked = PlayerState.currentLiked,
                    kept = Downloads.has(it.id),
                    keeping = if (Downloads.isRunning(it.id)) Downloads.progressOf(it.id) else null,
                )
            },
            volume = PlayerState.volume,
            onPlayPause = PlayerState::toggle,
            onToggleLike = PlayerState::toggleLike,
            onToggleMute = PlayerState::toggleMute,
            onKeep = PlayerState::downloadCurrent,
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

/**
 * What the middle pane is showing.
 *
 * An opened collection sits over whatever destination you were on, rather than
 * replacing it — going back puts you exactly where you left off, mid-scroll.
 */
@Composable
private fun Content(destination: Destination) {
    if (Navigator.settingsOpen) {
        SettingsScreen()
        return
    }

    val opened = Navigator.opened
    if (opened != null) {
        CollectionScreen(
            card = opened,
            onBack = Navigator::back,
            onOpen = Navigator::open,
            onPlay = PlayerState::play,
            onShuffle = PlayerState::shuffle,
            onPlayAll = PlayerState::playAll,
        )
        return
    }

    when (destination) {
        Destination.Home -> HomeScreen(
            // A song plays where it is; anything else is a place to go.
            onOpen = { card ->
                if (card.kind == Catalogue.Kind.Song) PlayerState.open(card)
                else Navigator.open(card)
            },
            onPlayAll = PlayerState::playAll,
        )
        Destination.Explore -> ExploreScreen()

        Destination.Library -> LibraryScreen(onOpen = Navigator::open)

        Destination.Liked -> TrackListScreen(
            title = "Liked songs",
            tracks = Library.liked,
            empty = "Songs you like will collect here",
            onPlay = PlayerState::play,
            onShuffle = PlayerState::shuffle,
        )

        Destination.History -> TrackListScreen(
            title = "History",
            tracks = Library.history,
            empty = "Nothing played yet",
            onPlay = PlayerState::play,
            onShuffle = PlayerState::shuffle,
            action = "Clear" to Library::clearHistory,
        )

        Destination.Downloads -> DownloadsScreen(
            onPlay = PlayerState::play,
            onShuffle = PlayerState::shuffle,
        )

        Destination.OnThisComputer -> LocalScreen(
            onPlay = PlayerState::play,
            onShuffle = PlayerState::shuffle,
        )

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
