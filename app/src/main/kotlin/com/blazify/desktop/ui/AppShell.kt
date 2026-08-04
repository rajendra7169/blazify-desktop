package com.blazify.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QueueMusic
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.SleepTimer
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Playlists
import com.blazify.desktop.data.Presence
import com.blazify.desktop.data.Scrobbler
import com.blazify.desktop.ui.screens.CollectionScreen
import com.blazify.desktop.ui.screens.DownloadsScreen
import com.blazify.desktop.ui.screens.ExploreScreen
import com.blazify.desktop.ui.screens.HomeScreen
import com.blazify.desktop.ui.screens.LibraryScreen
import com.blazify.desktop.ui.screens.LocalScreen
import com.blazify.desktop.ui.screens.SettingsScreen
import com.blazify.desktop.ui.screens.ShelfScreen
import com.blazify.desktop.ui.screens.ShowsScreen
import com.blazify.desktop.ui.screens.TogetherScreen
import com.blazify.desktop.ui.screens.TopSongsScreen
import com.blazify.desktop.ui.screens.Editing
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
    var timerOpen by remember { mutableStateOf(false) }
    var addingOpen by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }

    // The words come up with the player when that's been asked for, and the
    // panel goes back to however it was when the player is put away — opening
    // one screen shouldn't quietly change what you see on the one behind it.
    var lyricsWereOpen by remember { mutableStateOf(false) }
    LaunchedEffect(full) {
        if (!Look.lyricsWithPlayer) return@LaunchedEffect
        if (full) {
            lyricsWereOpen = lyricsOpen
            lyricsOpen = true
        } else {
            lyricsOpen = lyricsWereOpen
        }
    }

    // The accent follows the cover when asked to. Watched here because this is
    // the one place that outlives every screen — a colour that reset each time
    // you changed page would be worse than no colour at all.
    LaunchedEffect(PlayerState.current?.thumbnail, Look.dynamicColour) {
        ArtworkColour.follow(PlayerState.current?.thumbnail.takeIf { Look.dynamicColour })
    }

    // The chat client is told when the song or the play state changes, and not
    // otherwise — the end time goes with it, so it does its own ticking rather
    // than being told the same song is still playing once a second.
    LaunchedEffect(PlayerState.current?.id, PlayerState.playing) {
        Presence.show(PlayerState.current, PlayerState.playing, PlayerState.positionSeconds)
    }

    // A play is only a play once it has been most of a play. Watched from here
    // because this is the one composable that outlives every screen — the
    // decision must not depend on which page happens to be open.
    LaunchedEffect(PlayerState.current?.id) {
        val playing = PlayerState.current ?: return@LaunchedEffect
        snapshotFlow { PlayerState.positionSeconds }.collect { seconds ->
            Scrobbler.heard(playing, seconds)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Blz.page)) {
        Row(Modifier.weight(1f)) {
            // One rail at a time. The settings bring their own, and two lists
            // of unrelated places side by side is a question about which one
            // you're in that shouldn't have to be asked.
            if (!Navigator.settingsOpen) {
                Sidebar(
                    current = Navigator.destination,
                    collapsed = railCollapsed,
                    settingsOpen = false,
                    // Same for the rail: picking a place to go means going
                    // there, not queuing it up behind the player.
                    onSelect = { full = false; Navigator.go(it) },
                    // Out of the full player first. Settings opened behind a
                    // screen that covers the whole window is settings that
                    // didn't open, as far as anyone watching can tell.
                    onOpenSettings = { full = false; Navigator.openSettings() },
                )
            }

            Box(Modifier.weight(1f).fillMaxSize()) {
                // Over the content rather than instead of it: closing the full
                // view puts you back exactly where you were browsing.
                if (full) {
                    NowPlayingScreen(
                        lyricsOpen = lyricsOpen,
                        queueOpen = queueOpen,
                        timerOn = SleepTimer.running,
                        onToggleLyrics = { lyricsOpen = !lyricsOpen },
                        onToggleQueue = { queueOpen = !queueOpen },
                        onOpenTimer = { timerOpen = true },
                        onAddToPlaylist = { addingOpen = true },
                        onClose = { full = false },
                    )
                } else {
                    Content(Navigator.destination)
                }
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
                        onExpand = { Theatre.open = true },
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

        // Gone while the full player is up, and slid rather than switched:
        // every control down here has a larger twin on that screen, and two
        // sets of the same buttons is a question about which to press. It
        // leaves downward, the way it will come back.
        AnimatedVisibility(
            visible = !full,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
        ) {
            Column {
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
            onToggleShuffle = PlayerState::toggleShuffle,
            onCycleRepeat = PlayerState::cycleRepeat,
            shuffling = PlayerState.shuffling,
            repeat = PlayerState.repeat.ordinal,
            onToggleLyrics = { lyricsOpen = !lyricsOpen },
            onToggleQueue = { queueOpen = !queueOpen },
            onOpenTimer = { timerOpen = true },
            onAddToPlaylist = { addingOpen = true },
            // Opening the player closes the settings behind it, so putting it
            // away lands you on a page you can browse rather than back in a
            // list of preferences you had already finished with.
            onExpand = {
                if (!full) Navigator.closeSettings()
                full = !full
            },
            lyricsOpen = lyricsOpen,
            queueOpen = queueOpen,
            timerOn = SleepTimer.running,
        )
            }
        }
    }

        // The whole window, rail and transport included. Full screen that left
        // a sidebar showing would only be a bigger panel, and the reason for
        // this view is that nothing else is on the screen.
        if (Theatre.open) {
            LyricsTheatre(
                track = PlayerState.current,
                position = PlayerState.positionSeconds,
                onSeekTo = PlayerState::seekTo,
                onClose = { Theatre.open = false },
            )
        }

        // Above every screen, because somebody knocking is waiting on an
        // answer and you will not be looking at the room page when they do.
        KnockDialog()

        // Over everything, including the transport — a dialog that the bar
        // could be clicked through is not a dialog.
        if (timerOpen) SleepTimerDialog(onDismiss = { timerOpen = false })
        // Either the bar asked for the song that's playing, or a row asked for
        // one of its own.
        (Dialogs.addingTo ?: PlayerState.current?.takeIf { addingOpen })?.let {
            AddToPlaylistDialog(it, onDismiss = { addingOpen = false; Dialogs.dismiss() })
        }
        if (Dialogs.keepingQueue && PlayerState.queue.isNotEmpty()) {
            SaveQueueDialog(PlayerState.queue, onDismiss = Dialogs::dismiss)
        }
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

    Navigator.playlist?.let { id ->
        val playlist = Playlists.find(id)
        if (playlist == null) {
            Navigator.closePlaylist()
        } else {
            TrackListScreen(
                title = playlist.name,
                tracks = playlist.tracks,
                empty = "Nothing in here yet",
                onPlay = PlayerState::play,
                onShuffle = PlayerState::shuffle,
                emptyIcon = Icons.Rounded.QueueMusic,
                emptyDetail = "Right-click any song and add it to this playlist.",
                action = "Delete playlist" to {
                    Playlists.delete(id)
                    Navigator.closePlaylist()
                },
                onBack = Navigator::closePlaylist,
                // This is a list somebody made, so its name and its order are
                // theirs to change. Liked songs and history get none of this —
                // their order is an answer, not a choice.
                edit = Editing(
                    onRename = { Playlists.rename(id, it) },
                    onRemove = { Playlists.removeAt(id, it) },
                    onMove = { from, to -> Playlists.move(id, from, to) },
                ),
            )
            return
        }
    }

    // The whole of a shelf sits above whatever page offered it, so leaving it
    // puts you back on that page rather than at the top of the app.
    Navigator.expanded?.let { (title, more) ->
        ShelfScreen(
            title = title,
            more = more,
            onBack = Navigator::closeShelf,
            onOpen = Navigator::open,
        )
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
        Destination.Explore -> ExploreScreen(
            onOpen = Navigator::open,
            onPlayAll = PlayerState::playAll,
        )

        Destination.Podcasts -> ShowsScreen(onOpen = Navigator::open)

        Destination.Library -> LibraryScreen(
            onOpen = Navigator::open,
            onOpenPlaylist = Navigator::openPlaylist,
        )

        Destination.Liked -> TrackListScreen(
            title = "Liked songs",
            kind = "Your likes",
            tracks = Library.liked,
            empty = "Songs you like will collect here",
            emptyIcon = Icons.Rounded.Favorite,
            emptyDetail = "Press the heart beside anything that's playing, or right-click " +
                "a song anywhere and like it from there.",
            onPlay = PlayerState::play,
            onShuffle = PlayerState::shuffle,
        )

        Destination.TopSongs -> TopSongsScreen(
            onPlay = PlayerState::play,
            onShuffle = PlayerState::shuffle,
        )

        Destination.History -> TrackListScreen(
            title = "History",
            kind = "Recently played",
            tracks = Library.history,
            empty = "Nothing played yet",
            emptyIcon = Icons.Rounded.History,
            emptyDetail = "Everything you play lands here, newest first, so you can find " +
                "your way back to something you can't name.",
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

        Destination.Together -> TogetherScreen()

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
