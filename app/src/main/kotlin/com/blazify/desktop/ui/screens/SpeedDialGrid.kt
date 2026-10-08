package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Blocked
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Plays
import com.blazify.desktop.data.Playlists
import com.blazify.desktop.data.SpeedDial
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The tiles, worked out again only when the plays, the playlists or the
 * blocked artists change — not on every frame the home page draws.
 *
 * Empty until there is enough history to fill the grid honestly: three tiles
 * is not a speed dial, it is a list of three songs.
 */
@Composable
fun rememberSpeedDial(): List<SpeedDial.Dial> {
    val dials = remember(Plays.all, Playlists.all, Blocked.all, Library.history) {
        SpeedDial.build(Plays.all, Library.known(), Playlists.all)
    }
    return if (dials.size < 4) emptyList() else dials
}

/**
 * What you play most, as a grid you can hit without looking.
 *
 * Near the top of Home because it is the shortest way from opening the app to
 * hearing the thing you opened it for. Worked out from this machine's own
 * play counts, so it needs no network and is never somebody else's idea of
 * what you like.
 */
@Composable
fun SpeedDialGrid(dials: List<SpeedDial.Dial>) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("Speed dial", color = Blz.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            dials.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { dial -> Box(Modifier.weight(1f)) { Tile(dial) } }
                    // A short last row keeps its tiles the same width as the rest.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Tile(dial: SpeedDial.Dial) {
    when (dial) {
        is SpeedDial.Dial.Song -> SongMenu(dial.track) {
            TileBody(
                picture = dial.track.thumbnail,
                round = false,
                title = dial.track.title,
                detail = dial.track.artist,
            ) { PlayerState.play(listOf(dial.track), 0) }
        }
        // Their songs you've played most, most first: the reason they're here.
        is SpeedDial.Dial.Artist -> TileBody(
            picture = dial.picture,
            round = true,
            title = dial.name,
            detail = "Artist  ·  ${dial.songs.size} songs",
        ) { PlayerState.play(dial.songs, 0) }

        is SpeedDial.Dial.Playlist -> TileBody(
            picture = dial.playlist.covers.firstOrNull(),
            round = false,
            title = dial.playlist.name,
            detail = "Playlist  ·  ${dial.playlist.tracks.size} songs",
        ) { PlayerState.play(dial.playlist.tracks, 0) }
    }
}

@Composable
private fun TileBody(
    picture: String?,
    round: Boolean,
    title: String,
    detail: String,
    onPlay: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Blz.surface)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (picture == null && !round) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Blz.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.QueueMusic, null, Modifier.size(20.dp), tint = Blz.dim) }
            } else {
                Artwork(
                    picture,
                    size = 48.dp,
                    corner = if (round) 24.dp else 8.dp,
                    modifier = if (round) Modifier.clip(CircleShape) else Modifier,
                )
            }
            // What the tile does, where the pointer is.
            if (hovered.value) {
                Box(
                    Modifier.size(48.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(24.dp), tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title, color = Blz.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail, color = Blz.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
