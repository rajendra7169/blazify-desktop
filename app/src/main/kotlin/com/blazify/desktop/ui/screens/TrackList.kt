package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.EmptyState
import com.blazify.desktop.ui.SongMenu
import com.blazify.desktop.ui.SongSheetButton
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What can be done to a list that belongs to you.
 *
 * Liked songs and history are lists the app keeps *about* you and there is
 * nothing to edit in them — the order is the answer to a question, not a
 * choice. A playlist is the opposite: its name and its order are the whole of
 * what somebody made. Passed as one thing so a screen either has all three or
 * none, and can't end up half-editable.
 */
data class Editing(
    val onRename: (String) -> Unit,
    val onRemove: (Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
)

/**
 * A screen that is one list of songs, with a title over it.
 *
 * Liked songs and history are the same thing wearing different words, and a
 * page of songs is exactly as useful empty as it is full — so the empty state
 * says what would put something here rather than apologising for the blank.
 */
@Composable
fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    empty: String,
    emptyIcon: ImageVector = Icons.Rounded.Favorite,
    emptyDetail: String = "",
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    action: Pair<String, () -> Unit>? = null,
    onBack: (() -> Unit)? = null,
    kind: String = "Playlist",
    edit: Editing? = null,
) {
    if (tracks.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            onBack?.let { back ->
                val (source, hovered) = rememberHovered()
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .padding(start = 26.dp, top = 22.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .hoverBackground(Blz.hover, hovered, source)
                        .clickable(onClick = back)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(17.dp), tint = Blz.muted)
                    Text("Back", color = Blz.muted, fontSize = 13.sp)
                }
            }
            EmptyState(emptyIcon, title, emptyDetail.ifBlank { empty })
        }
        return
    }

    // Where the row being dragged has got to. Held out here rather than in the
    // row, because the row it is being dragged past has to know about it too,
    // and because a row that moves would otherwise take the drag with it.
    val drag = remember { Drag() }
    val step = with(LocalDensity.current) { (ROW + GAP).toPx() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        onBack?.let { back ->
            item {
                val (source, hovered) = rememberHovered()
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .hoverBackground(Blz.hover, hovered, source)
                        .clickable(onClick = back)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(17.dp), tint = Blz.muted)
                    Text("Back", color = Blz.muted, fontSize = 13.sp)
                }
            }
        }

        item {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Collage(tracks)

                Column(
                    Modifier.weight(1f).padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        kind.uppercase(), color = Blz.muted, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                    )
                    Title(title, edit?.onRename)
                    Text(
                        listOfNotNull(
                            "${tracks.size} songs",
                            length(tracks),
                        ).joinToString("  ·  "),
                        color = Blz.muted, fontSize = 13.sp,
                    )

                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(Icons.Rounded.PlayArrow, "Play", filled = true) { onPlay(tracks, 0) }
                        Pill(Icons.Rounded.Shuffle, "Shuffle", filled = false) { onShuffle(tracks) }
                        action?.let { (label, onClick) -> TextAction(label, onClick) }
                    }
                }
            }
        }

        itemsIndexed(
            tracks,
            // Keyed by the song itself where the order can change: an index in
            // the key means a row that moves is a row that is destroyed and
            // built again, which drops the drag halfway through it.
            key = { at, track -> if (edit != null) track.id else "$at-${track.id}" },
        ) { at, track ->
            val dragged = drag.at == at
            SongMenu(track) {
                Row(
                    track,
                    at + 1,
                    onPlay = { onPlay(tracks, at) },
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) drag.by else 0f },
                ) { hovered ->
                    if (edit != null && (hovered || dragged)) {
                        RowButton(Icons.Rounded.Close, "Remove from playlist") { edit.onRemove(at) }
                        Grip(
                            onStart = { drag.start(at) },
                            onMove = { moved -> drag.advance(moved, step, tracks.lastIndex, edit.onMove) },
                            onEnd = drag::stop,
                        )
                    }
                }
            }
        }
    }
}

/** The height of a row and the space under it, which is one step of a drag. */
private val ROW = 58.dp
private val GAP = 14.dp

/**
 * A row on its way somewhere else.
 *
 * A drag is not a single move but a run of them, and each one has to be told
 * to the playlist as it happens rather than at the end — otherwise the list
 * under the finger doesn't move until the finger stops, which reads as the
 * drag having failed.
 */
private class Drag {
    var at by mutableStateOf<Int?>(null)
        private set
    var by by mutableStateOf(0f)
        private set

    fun start(index: Int) { at = index; by = 0f }

    fun stop() { at = null; by = 0f }

    fun advance(delta: Float, step: Float, last: Int, move: (Int, Int) -> Unit) {
        val from = at ?: return
        by += delta
        // Past the neighbour by a whole row: swap, and carry the remainder so
        // a long drag keeps going rather than restarting at each boundary.
        when {
            by >= step && from < last -> { move(from, from + 1); at = from + 1; by -= step }
            by <= -step && from > 0 -> { move(from, from - 1); at = from - 1; by += step }
        }
    }
}

/**
 * The playlist's name, which is also the way to change it.
 *
 * No dialog: a name is one line of text and asking for a window to type it in
 * is a ceremony around something that should feel like correcting a label.
 * Enter keeps it, Escape leaves it as it was.
 */
@Composable
private fun Title(title: String, onRename: ((String) -> Unit)?) {
    val (source, hovered) = rememberHovered()
    var typing by remember(title) { mutableStateOf(false) }

    if (typing && onRename != null) {
        var draft by remember { mutableStateOf(title) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }

        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { key ->
                    when {
                        key.type != KeyEventType.KeyDown -> false
                        key.key == Key.Enter -> { onRename(draft); typing = false; true }
                        key.key == Key.Escape -> { typing = false; true }
                        else -> false
                    }
                },
            textStyle = TextStyle(color = Blz.ink, fontSize = 38.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Blaze.Amber),
            singleLine = true,
        )
        return
    }

    androidx.compose.foundation.layout.Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onRename != null) Modifier.clickable { typing = true } else Modifier)
            .hoverBackground(androidx.compose.ui.graphics.Color.Transparent, hovered, source),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title, color = Blz.ink, fontSize = 38.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        // Only for a list that has a name of somebody's choosing, and only
        // once they've shown interest in it by pointing at it.
        if (onRename != null && hovered.value) {
            Icon(Icons.Rounded.Edit, "Rename", Modifier.size(18.dp), tint = Blz.muted)
        }
    }
}

/** The part of a row you take hold of to move it. */
@Composable
private fun Grip(onStart: () -> Unit, onMove: (Float) -> Unit, onEnd: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = onEnd,
                    onDragCancel = onEnd,
                    onDrag = { change, moved -> change.consume(); onMove(moved.y) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.DragIndicator, "Reorder", Modifier.size(18.dp), tint = Blz.muted)
    }
}

@Composable
private fun RowButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(17.dp), tint = if (hovered.value) Blz.ink else Blz.muted)
    }
}

/**
 * Cover art for a list that has none of its own.
 *
 * A playlist is only ever the songs in it, so the first four of them stand in.
 * Four rather than one because a single cover claims the list is that record —
 * a grid says at a glance that it's a collection, before a word is read.
 */
@Composable
fun Collage(tracks: List<Track>, size: androidx.compose.ui.unit.Dp = 210.dp) {
    val covers = tracks.mapNotNull { it.thumbnail }.take(4)
    val half = size / 2

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Blz.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            covers.size >= 4 -> Column {
                androidx.compose.foundation.layout.Row {
                    Artwork(covers[0], size = half, corner = 0.dp)
                    Artwork(covers[1], size = half, corner = 0.dp)
                }
                androidx.compose.foundation.layout.Row {
                    Artwork(covers[2], size = half, corner = 0.dp)
                    Artwork(covers[3], size = half, corner = 0.dp)
                }
            }
            covers.isNotEmpty() -> Artwork(covers.first(), size = size, corner = 0.dp)
            else -> Icon(Icons.Rounded.QueueMusic, null, Modifier.size(46.dp), tint = Blz.dim)
        }
    }
}

/** How long the whole thing runs, when every song has said. */
private fun length(tracks: List<Track>): String? {
    val seconds = tracks.sumOf { it.durationSeconds ?: 0 }
    if (seconds <= 0) return null
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}

@Composable
private fun Row(
    track: Track,
    number: Int,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable (hovered: Boolean) -> Unit = {},
) {
    val (source, hovered) = rememberHovered()
    val liked = Library.isLiked(track.id)

    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .height(ROW)
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (hovered.value) {
                Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(19.dp), tint = Blz.ink)
            } else {
                Text("$number", color = Blz.dim, fontSize = 13.sp)
            }
        }
        Artwork(track.thumbnail, size = 42.dp)
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
        if (liked) {
            Icon(Icons.Rounded.Favorite, "Liked", Modifier.size(15.dp), tint = Blaze.Amber)
        }
        if (track.duration.isNotEmpty()) {
            Text(track.duration, color = Blz.dim, fontSize = 12.sp)
        }
        extra(hovered.value)
        SongSheetButton(track, hovered.value)
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    androidx.compose.foundation.layout.Row(
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
private fun TextAction(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = Blz.muted, fontSize = 12.5.sp)
    }
}
