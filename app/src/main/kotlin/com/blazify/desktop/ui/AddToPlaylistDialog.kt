package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.Typing
import com.blazify.desktop.data.Playlists
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Putting a song somewhere.
 *
 * Each playlist says whether the song is already in it, so pressing the same
 * one twice isn't a question you have to remember the answer to. Making a new
 * one puts the song straight in — that's why anyone is making it.
 */
@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    var naming by remember { mutableStateOf(Playlists.all.isEmpty()) }
    var name by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.OnAmber.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Add to playlist", color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    track.title, color = Blz.muted, fontSize = 12.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            if (Playlists.all.isNotEmpty()) {
                LazyColumn(
                    Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(Playlists.all, key = { at, it -> "$at-${it.id}" }) { _, playlist ->
                        val inside = Playlists.contains(playlist.id, track.id)
                        val (source, hovered) = rememberHovered()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .hoverBackground(Blz.hover, hovered, source)
                                .clickable {
                                    Playlists.add(playlist.id, track)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Icon(Icons.Rounded.QueueMusic, null, Modifier.size(17.dp), tint = Blz.dim)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playlist.name, color = Blz.ink, fontSize = 13.5.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${playlist.tracks.size} songs",
                                    color = Blz.dim, fontSize = 11.5.sp,
                                )
                            }
                            if (inside) {
                                Icon(
                                    Icons.Rounded.Check, "Already in", Modifier.size(16.dp),
                                    tint = Blaze.Amber,
                                )
                            }
                        }
                    }
                }
            }

            if (naming) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Blz.surfaceHigh)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    if (name.isEmpty()) {
                        Text("Name it", color = Blz.dim, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Blz.ink, fontSize = 13.sp),
                        cursorBrush = SolidColor(Blaze.Amber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { Typing.active = it.isFocused },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (naming) {
                    Pill("Create", filled = true, Modifier.weight(1f)) {
                        Playlists.create(name, listOf(track))
                        onDismiss()
                    }
                } else {
                    Pill("New playlist", filled = false, Modifier.weight(1f)) { naming = true }
                }
                Pill("Cancel", filled = false, Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun Pill(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surfaceHigh),
            )
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) Blaze.OnAmber else Blz.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
