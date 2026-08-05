package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.Trouble
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.SongSheetButton
import com.blazify.desktop.ui.SkeletonRows
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * One album, playlist or artist, opened.
 *
 * Everything you'd want before committing to it sits above the fold: the
 * artwork at a size worth showing, what it is, how much of it there is, and the
 * two buttons that start it. The list below is numbered, because a track's
 * place on a record is part of what it is.
 */
@Composable
fun CollectionScreen(
    card: Catalogue.Card,
    onBack: () -> Unit,
    onOpen: (Catalogue.Card) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onPlayAll: (List<Catalogue.Card>, Int) -> Unit,
) {
    var page by remember(card.id) { mutableStateOf<Catalogue.Collection?>(null) }
    var problem by remember(card.id) { mutableStateOf<String?>(null) }

    suspend fun fetch() {
        problem = null
        Catalogue.collection(card).fold(
            onSuccess = { page = it },
            onFailure = { problem = "Couldn't open ${card.title}" },
        )
    }

    LaunchedEffect(card.id) { fetch() }

    val shown = page?.card ?: card

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { BackRow(onBack) }
        item { Header(shown, page, onPlay, onShuffle) }

        when {
            problem != null -> item { Trouble(problem!!) { fetch() } }
            page == null -> item { SkeletonRows(count = 8) }
            page!!.shelves.isNotEmpty() -> items(page!!.shelves) { shelf ->
                Shelf(shelf, onOpen, onPlayAll)
            }
            else -> itemsIndexed(page!!.tracks, key = { at, t -> "$at-${t.id}" }) { at, track ->
                SongMenu(track) { TrackRow(at + 1, track) { onPlay(page!!.tracks, at) } }
            }
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
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
        Text("Back", color = Blz.muted, fontSize = 13.sp)
    }
}

@Composable
private fun Header(
    card: Catalogue.Card,
    page: Catalogue.Collection?,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
) {
    val round = card.kind == Catalogue.Kind.Artist
    val tracks = page?.tracks.orEmpty()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Artwork(
            card.thumbnail,
            size = 210.dp,
            corner = if (round) 105.dp else 14.dp,
            modifier = if (round) Modifier.clip(CircleShape) else Modifier,
        )
        Column(
            Modifier.weight(1f).padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (Catalogue.isShow(card.id)) "PODCAST" else card.kind.name.uppercase(),
                color = Blz.muted, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
            )
            Text(
                card.title, color = Blz.ink, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    card.subtitle.takeIf { it.isNotEmpty() },
                    page?.note,
                    tracks.size.takeIf { it > 0 }?.let { "$it songs" },
                ).joinToString("  ·  "),
                color = Blz.muted, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tracks.isNotEmpty()) {
                    Action(Icons.Rounded.PlayArrow, "Play", filled = true) { onPlay(tracks, 0) }
                    Action(Icons.Rounded.Shuffle, "Shuffle", filled = false) { onShuffle(tracks) }
                }
                // Songs aren't saved from here — the heart in the bar is where
                // that lives, and two ways to do one thing is one too many.
                if (card.kind != Catalogue.Kind.Song) {
                    val saved = Library.isSaved(card.id)
                    // A show is followed, not saved: what you want from it is
                    // the next episode, and "Saved" describes keeping a copy of
                    // the last one.
                    val show = Catalogue.isShow(card.id)
                    Action(
                        if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        when {
                            show && saved -> "Following"
                            show -> "Follow"
                            saved -> "Saved"
                            else -> "Save"
                        },
                        filled = false,
                    ) { Library.toggleSaved(card) }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surface),
            )
            .then(
                if (filled) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val ink = if (filled) Blaze.OnAmber else Blz.ink
        Icon(icon, label, Modifier.size(19.dp), tint = ink)
        Text(label, color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrackRow(number: Int, track: Track, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    var opened by remember(track.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            // The number is what the row is; hovering swaps it for what the row
            // does, in the same place, so nothing shifts.
            if (hovered.value) {
                Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(19.dp), tint = Blz.ink)
            } else {
                Text("$number", color = Blz.dim, fontSize = 13.sp)
            }
        }
        Artwork(track.thumbnail, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.title, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Blz.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.duration.isNotEmpty()) {
            Text(track.duration, color = Blz.dim, fontSize = 12.sp)
        }

        // Only where there is something to open. An album track has no
        // paragraph about it and a chevron promising one would be a lie.
        if (track.notes != null) {
            val (noteSource, noteHovered) = rememberHovered()
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .hoverBackground(Blz.hover, noteHovered, noteSource)
                    .clickable { opened = !opened },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (opened) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    if (opened) "Less" else "What this is about",
                    Modifier.size(18.dp),
                    tint = if (opened || noteHovered.value) Blz.ink else Blz.muted,
                )
            }
        }

        SongSheetButton(track, hovered.value)
    }

    // Underneath rather than in a window of its own: it belongs to the row it
    // came from, and a dialog would put the list away to say one paragraph
    // about one thing in it.
    if (opened && track.notes != null) {
        Text(
            track.notes,
            color = Blz.muted, fontSize = 12.5.sp, lineHeight = 19.sp,
            modifier = Modifier.padding(start = 92.dp, end = 40.dp, bottom = 14.dp),
        )
    }
    }
}
