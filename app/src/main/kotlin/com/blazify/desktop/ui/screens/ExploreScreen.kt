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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.Typing
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SkeletonRows
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.delay

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

@Composable
fun ExploreScreen() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Search as you type, but only once you've stopped. Firing on every
    // keystroke would send a request per letter and show results for a prefix
    // nobody meant to search for.
    LaunchedEffect(query) {
        val typed = query.trim()
        if (typed.length < 2) {
            results = emptyList(); searching = false; message = null
            return@LaunchedEffect
        }
        searching = true
        message = null
        delay(350)
        Catalogue.search(typed).fold(
            onSuccess = {
                results = it
                message = if (it.isEmpty()) "Nothing matched that" else null
            },
            onFailure = { message = "Couldn't reach the catalogue" },
        )
        searching = false
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SearchField(query) { query = it }

        when {
            searching && results.isEmpty() -> SkeletonRows(count = 8)
            message != null -> Text(message!!, color = Blz.muted, fontSize = 13.sp)
            results.isEmpty() -> Text(
                "Search for a song, an artist, anything.",
                color = Blz.dim, fontSize = 13.sp,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(results, key = { _, t -> t.id }) { i, track ->
                    TrackRow(
                        position = i + 1,
                        track = track,
                        playing = PlayerState.current?.id == track.id,
                        onPlay = { PlayerState.play(results, i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Blz.surface)
            .hoverBackground(Blz.hover, hovered, source)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = Blz.dim)
        Box(Modifier.fillMaxWidth()) {
            if (value.isEmpty()) {
                Text("Search songs, artists, albums", color = Blz.dim, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Blz.ink, fontSize = 13.sp),
                cursorBrush = SolidColor(Blaze.Amber),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { Typing.active = it.isFocused },
            )
        }
    }
}

@Composable
private fun TrackRow(position: Int, track: Track, playing: Boolean, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    val accent = if (playing) Blaze.Amber else Blz.ink

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Text(
                if (playing) "♪" else "$position",
                color = if (playing || hovered.value) Blaze.Amber else Blz.dim,
                fontSize = 11.5.sp,
            )
        }
        Artwork(track.thumbnail, size = 38.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(track.duration, color = Blz.muted, fontSize = 12.sp)
        Icon(
            Icons.Rounded.MoreVert, "More", Modifier.size(17.dp),
            tint = if (hovered.value) Blz.muted else Blz.muted.copy(alpha = 0f),
        )
    }
}
