package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Mark
import com.blazify.desktop.data.Resume
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The things you are part of the way through.
 *
 * High on the page and above everything the catalogue suggests, because a
 * half-finished episode is the one thing here that is already yours — it beats
 * any recommendation, and burying it under a wall of tiles is how a long
 * recording quietly becomes one you never went back to.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ContinueRail(marks: List<Mark>) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Pick up where you left off",
            color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        )
        LazyRow(
            state = state,
            // A wheel turned sideways moves the row; turned the ordinary way it
            // belongs to the page, which is what somebody scrolling past this
            // is doing.
            modifier = Modifier.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll) { event ->
                val turned = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                if (turned.x == 0f) return@onPointerEvent
                scope.launch { state.scrollBy(turned.x * 64f) }
            },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(marks, key = { it.track.id }) { mark -> ContinueCard(mark) }
        }
    }
}

@Composable
private fun ContinueCard(mark: Mark) {
    val (source, hovered) = rememberHovered()

    Column(
        Modifier
            .width(196.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { PlayerState.play(listOf(mark.track), 0, "Continuing") }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(180.dp)) {
            Artwork(mark.track.thumbnail, size = 180.dp, corner = 10.dp)

            // The play affordance and the way to be rid of it appear together,
            // on the artwork, so neither takes room from the title when the
            // pointer is elsewhere.
            if (hovered.value) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow, "Continue",
                        Modifier.size(26.dp), tint = Blaze.OnAmber,
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Blz.page.copy(alpha = 0.75f))
                        .clickable { Resume.forget(mark.track.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close, "Forget this",
                        Modifier.size(15.dp), tint = Blz.ink,
                    )
                }
            }
        }

        // How far in, as a line rather than a number: the shape of it says at a
        // glance whether this is nearly done or barely begun, which is the
        // thing being decided when someone looks at this row.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Blz.surfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(mark.fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
            )
        }

        Text(
            mark.track.title, color = Blz.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                mark.left, color = Blaze.Amber, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                mark.track.artist, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
