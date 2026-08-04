package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import com.blazify.desktop.PlayerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What's coming, down the right-hand side.
 *
 * A panel rather than a sheet: on a wide screen there's room to keep it open
 * while you carry on browsing, which is the whole reason to have a desktop
 * version. It scrolls to whatever is playing when you open it — with a hundred
 * tracks loaded, opening at the top would be useless.
 */
/**
 * A way of looking at a queue, rather than a different queue.
 *
 * YouTube's own chips ask its server for a different mix; these can't, because
 * nobody hands us one. What they can do is sort the queue you already have into
 * the questions people actually ask of it — what do I know, what is new, what
 * did I already keep — every one of which is answerable from what's on this
 * machine. Naming them after something we cannot tell (a mood, a chart
 * position) would be five buttons that shuffle the list and mean nothing.
 */
private enum class Lens(val label: String, val keep: (Track, Int, Int) -> Boolean) {
    All("All", { _, _, _ -> true }),
    UpNext("Up next", { _, at, current -> at > current }),
    Familiar("Familiar", { track, _, _ ->
        Library.history.any { it.id == track.id } || Library.isLiked(track.id)
    }),
    Discover("Discover", { track, _, _ ->
        Library.history.none { it.id == track.id } && !Library.isLiked(track.id)
    }),
    Liked("Liked", { track, _, _ -> Library.isLiked(track.id) }),
    Offline("Offline", { track, _, _ -> Downloads.has(track.id) }),
}

@Composable
fun QueuePanel(
    queue: List<Track>,
    current: Int,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var lens by remember { mutableStateOf(Lens.All) }

    // Kept as (position, track) so a filtered row still knows where it lives in
    // the real queue — jumping to "the third familiar one" has to mean jumping
    // to song nineteen, not to song three.
    val shown = remember(queue, current, lens) {
        queue.withIndex().filter { (at, track) -> lens.keep(track, at, current) }
    }

    /**
     * Whether the order can be changed by hand here.
     *
     * Two conditions, both of them about honesty rather than difficulty. A
     * filtered list is showing every third song, so dragging one down by a row
     * would move it past songs nobody can see — the gesture would mean
     * something different from what it looks like. And a queue holding the
     * same song twice cannot key its rows by the song, which is what lets a
     * row survive being moved; without that the drag dies halfway through
     * itself. Neither is common, and in both cases the grip simply isn't there.
     */
    val reorderable = lens == Lens.All &&
        remember(queue) { queue.map { it.id }.toSet().size == queue.size }

    // Where the row being dragged has got to. Held here rather than in the row:
    // the row it is being dragged past has to know, and a row that moves would
    // otherwise take the drag with it.
    val drag = remember { QueueDrag() }
    val step = with(androidx.compose.ui.platform.LocalDensity.current) { 53.dp.toPx() }

    LaunchedEffect(current, queue.size) {
        if (current in queue.indices) {
            listState.scrollToItem(current.coerceAtLeast(0))
        }
    }

    Column(
        modifier
            // Wider than a sidebar wants to be, because a queue row carries a
            // cover, two lines of text and a length — at 274dp every title was
            // an ellipsis and the list read as a column of half-names.
            .width(348.dp)
            .fillMaxHeight()
            .background(Blz.rail)
            .padding(start = 14.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Playing from", color = Blz.dim, fontSize = 11.sp)
                Text(
                    PlayerState.playingFrom ?: "Your queue",
                    color = Blz.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            // A queue is the one list nobody builds on purpose — it collects
            // out of what you played, what you added and where a radio went,
            // and it is gone the moment you play something else. Keeping it is
            // the difference between an evening you can play again and one you
            // can only remember.
            if (queue.isNotEmpty()) {
                val (source, hovered) = rememberHovered()
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Blz.surfaceHigh)
                        .hoverBackground(Blz.hover, hovered, source)
                        .clickable { Dialogs.keepQueue() }
                        .padding(start = 13.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(16.dp), tint = Blz.ink)
                    Text("Save", color = Blz.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Sideways, and the wheel does it: a row of chips with a scrollbar
        // under it is two controls for one gesture, and nobody drags a
        // horizontal bar when the wheel is already under their hand.
        LensStrip(lens) { lens = it }

        if (queue.isEmpty()) {
            Text("Nothing queued yet", color = Blz.dim, fontSize = 12.sp)
            return@Column
        }

        // No scrollbar drawn beside it. The wheel scrolls it, the current song
        // scrolls itself into view, and a permanent grey stripe down the edge
        // of a 350dp panel is a third of a column spent saying "this is a
        // list", which the list already says.
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        "Nothing in this queue matches ${lens.label.lowercase()}.",
                        color = Blz.dim, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
            items(
                shown,
                key = { (at, track) -> if (reorderable) track.id else "$at-${track.id}" },
            ) { (at, track) ->
                QueueRow(
                    track = track,
                    playing = at == current,
                    upcoming = at > current,
                    dragged = drag.at == at,
                    offset = if (drag.at == at) drag.by else 0f,
                    onJump = { onJump(at) },
                    onRemove = { onRemove(at) },
                    grip = if (!reorderable) null else ({
                        drag.start(at)
                    } to { moved: Float ->
                        drag.advance(moved, step, queue.lastIndex, onMove)
                    }),
                    onGripEnd = drag::stop,
                )
            }
        }
    }
}

/**
 * The chips, scrolled by the wheel.
 *
 * A vertical wheel turn moves this sideways, because on a strip this shape
 * that is the only wheel anyone has — and a horizontal scrollbar drawn under
 * six words would be taller than the words.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LensStrip(current: Lens, onPick: (Lens) -> Unit) {
    val state = rememberScrollState()
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val turned = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                val by = if (turned.x != 0f) turned.x else turned.y
                scope.launch { state.scrollBy(by * 64f) }
            }
            .horizontalScroll(state),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Lens.entries.forEach { option ->
            val on = option == current
            val (source, hovered) = rememberHovered()
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (on) {
                            Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                        } else {
                            Modifier.background(Blz.surfaceHigh)
                        },
                    )
                    .then(
                        if (on) Modifier.hoverGlow(hovered, source)
                        else Modifier.hoverBackground(Blz.hover, hovered, source),
                    )
                    .clickable { onPick(option) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    option.label,
                    color = if (on) Blaze.OnAmber else Blz.muted,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        // So the last chip can be scrolled clear of the panel's edge.
        Box(Modifier.size(8.dp))
    }
}

@Composable
private fun QueueRow(
    track: Track,
    playing: Boolean,
    upcoming: Boolean,
    dragged: Boolean = false,
    offset: Float = 0f,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    grip: Pair<() -> Unit, (Float) -> Unit>? = null,
    onGripEnd: () -> Unit = {},
) {
    val (source, hovered) = rememberHovered()
    // Whether this row's menu is open, held by the row rather than by the
    // button. The pointer leaves the row the moment it moves onto the menu, and
    // a button that only exists while the row is hovered takes its own menu
    // down with it — which is why the dots opened nothing you could reach.
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .zIndex(if (dragged) 1f else 0f)
            .graphicsLayer { translationY = offset }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (playing || menuOpen || dragged) Modifier.background(Blz.surfaceHigh)
                else Modifier,
            )
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onJump)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // The cover, or a play mark for the one that's on. Swapped rather than
        // shown alongside: the row that is playing needs to be findable in a
        // glance down the list, and a second small square beside forty others
        // is not what finds it.
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                Icon(
                    Icons.Rounded.PlayArrow, "Playing",
                    Modifier.size(22.dp), tint = Blaze.Amber,
                )
            } else {
                Artwork(track.thumbnail, size = 38.dp, corner = 5.dp)
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.title,
                // Played tracks stay visible but recede, so the line between
                // done and coming is readable at a glance.
                color = if (playing) Blaze.Amber else if (upcoming) Blz.ink else Blz.dim,
                fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        // Taken hold of to move the song. Only where moving it means what it
        // looks like — see above — and only once the pointer is on the row.
        if (grip != null) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (hovered.value || dragged) {
                    Icon(
                        Icons.Rounded.DragIndicator, "Reorder",
                        Modifier
                            .size(16.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(track.id) {
                                detectDragGestures(
                                    onDragStart = { grip.first() },
                                    onDragEnd = onGripEnd,
                                    onDragCancel = onGripEnd,
                                    onDrag = { change, moved ->
                                        change.consume()
                                        grip.second(moved.y)
                                    },
                                )
                            },
                        tint = Blz.muted,
                    )
                }
            }
        }

        // The length, until the pointer arrives — then everything you can do
        // to the song. One column, so nothing shifts sideways on hover, and
        // the same menu as every other list: what you can do with a song
        // shouldn't depend on which screen you found it on. Removing it from
        // here is in there too, which is why there's no separate cross.
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (hovered.value || menuOpen) {
                val (dotSource, dotHovered) = rememberHovered()
                Box {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .hoverBackground(Blz.hover, dotHovered, dotSource)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MoreVert, "More", Modifier.size(18.dp), tint = Blz.ink)
                    }
                    SongSheet(track, menuOpen, { menuOpen = false }, onRemove)
                }
            } else {
                Text(track.duration, color = Blz.dim, fontSize = 11.5.sp)
            }
        }
    }
}


/**
 * A queued song on its way somewhere else.
 *
 * A drag is a run of single steps rather than one jump, and each has to reach
 * the queue as it happens — a list that does not move until the pointer stops
 * reads as a drag that failed.
 */
private class QueueDrag {
    var at by mutableStateOf<Int?>(null)
        private set
    var by by mutableStateOf(0f)
        private set

    fun start(index: Int) { at = index; by = 0f }

    fun stop() { at = null; by = 0f }

    fun advance(delta: Float, step: Float, last: Int, move: (Int, Int) -> Unit) {
        val from = at ?: return
        by += delta
        when {
            by >= step && from < last -> { move(from, from + 1); at = from + 1; by -= step }
            by <= -step && from > 0 -> { move(from, from - 1); at = from - 1; by += step }
        }
    }
}
