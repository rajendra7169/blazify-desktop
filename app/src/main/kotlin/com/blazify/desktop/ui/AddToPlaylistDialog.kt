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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.blazify.desktop.Typing
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Catalogue
import kotlinx.coroutines.launch
import com.blazify.desktop.data.Playlists
import com.blazify.desktop.data.Track

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Putting a song somewhere.
 *
 * Both sets of playlists in one list — the ones on this machine and the ones on
 * the account — because "which of my playlists" is one question and answering
 * it in two places is the app's problem, not the listener's. Each says whether
 * the song is already in it, so pressing the same one twice isn't a question
 * you have to remember the answer to.
 *
 * A new playlist goes onto the account when there is one, so it is there on the
 * phone too. Made here when there isn't, which still works with the network
 * down — and nothing is lost either way.
 */
@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf<String?>(null) }

    var theirs by remember { mutableStateOf<List<Catalogue.AccountPlaylist>?>(null) }
    val scope = rememberCoroutineScope()

    // Asked for once each time the dialog opens rather than held: a playlist
    // made on the phone five minutes ago should be in this list, and a cached
    // copy is exactly how it wouldn't be.
    LaunchedEffect(Account.signedIn) {
        theirs = if (Account.signedIn) Catalogue.myPlaylists().getOrNull().orEmpty() else emptyList()
    }

    // Nothing anywhere means there is only one thing to do, so do it.
    LaunchedEffect(theirs) {
        if (theirs != null && theirs!!.isEmpty() && Playlists.all.isEmpty()) naming = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.Scrim)
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

            when {
                theirs == null -> Text("Looking for your playlists…", color = Blz.dim, fontSize = 12.5.sp)
                Playlists.all.isEmpty() && theirs!!.isEmpty() ->
                    Text(
                        when {
                            // Told apart, because "make one" and "sign in
                            // again" are opposite instructions and guessing
                            // wrong wastes the whole dialog.
                            Account.hasCredential && !Account.signedIn ->
                                "Your sign-in has lapsed, so your account's playlists aren't " +
                                    "listed. Settings › Account › Use current browser. You can " +
                                    "still make one here."
                            Account.signedIn -> "No playlists yet — make the first one."
                            else -> "No playlists on this computer yet. Sign in to see the ones on your account."
                        },
                        color = Blz.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
                    )
                else -> LazyColumn(
                    Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (Account.hasCredential && !Account.signedIn) {
                        item {
                            Text(
                                "Signed out — account playlists not listed.",
                                color = Blaze.Amber, fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (theirs!!.isNotEmpty()) {
                        item { Heading("ON YOUR ACCOUNT") }
                        itemsIndexed(theirs!!, key = { at, it -> "acc-$at-${it.id}" }) { _, playlist ->
                            Choice(
                                name = playlist.name,
                                detail = playlist.count ?: "On your account",
                                covers = listOfNotNull(playlist.thumbnail),
                                inside = false,
                                busy = working,
                            ) {
                                working = true
                                scope.launch {
                                    Catalogue.addToAccountPlaylist(playlist.id, track.id)
                                        .onSuccess { onDismiss() }
                                        .onFailure {
                                            working = false
                                            trouble = "Couldn't add it to ${playlist.name}."
                                        }
                                }
                            }
                        }
                    }
                    if (Playlists.all.isNotEmpty()) {
                        item { Heading("ON THIS COMPUTER") }
                        itemsIndexed(Playlists.all, key = { at, it -> "own-$at-${it.id}" }) { _, playlist ->
                            Choice(
                                name = playlist.name,
                                detail = "${playlist.tracks.size} songs",
                                covers = playlist.covers,
                                inside = Playlists.contains(playlist.id, track.id),
                                busy = false,
                            ) {
                                Playlists.add(playlist.id, track)
                                onDismiss()
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

            trouble?.let {
                Text("$it Try again, or add it to one on this computer.",
                    color = Blaze.Amber, fontSize = 11.5.sp, lineHeight = 17.sp)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (naming) {
                    Pill(
                        if (working) "Making…" else "Create",
                        filled = true, Modifier.weight(1f),
                    ) {
                        if (working || name.isBlank()) return@Pill
                        if (Account.signedIn) {
                            working = true
                            scope.launch {
                                Catalogue.createAccountPlaylist(name)
                                    .mapCatching { id ->
                                        Catalogue.addToAccountPlaylist(id, track.id).getOrThrow()
                                    }
                                    .onSuccess { onDismiss() }
                                    .onFailure {
                                        // Not lost. The account refused it, so
                                        // it is made here instead and the song
                                        // still ends up somewhere.
                                        Playlists.create(name, listOf(track))
                                        onDismiss()
                                    }
                            }
                        } else {
                            Playlists.create(name, listOf(track))
                            onDismiss()
                        }
                    }
                } else {
                    Pill("New playlist", filled = false, Modifier.weight(1f)) { naming = true }
                }
                Pill("Cancel", filled = false, Modifier.weight(1f), onClick = onDismiss)
            }

            if (naming) {
                Text(
                    if (Account.signedIn) "Made on your account, so it's on your phone too."
                    else "Made on this computer. Sign in to have it follow you.",
                    color = Blz.dim, fontSize = 11.5.sp,
                )
            }
        }
    }
}

/**
 * A playlist's face, at the size of a list row.
 *
 * One cover if that's all there is, four in a square if there are four — the
 * same tile the library draws, because a playlist recognised in one place and
 * anonymous in another is two different playlists as far as the eye is
 * concerned. A note only when there's nothing in it yet.
 */
@Composable
private fun PlaylistArt(covers: List<String>) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)).background(Blz.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            covers.size >= 4 -> Column {
                repeat(2) { row ->
                    Row {
                        repeat(2) { column ->
                            Artwork(covers[row * 2 + column], size = 19.dp, corner = 0.dp)
                        }
                    }
                }
            }
            covers.isNotEmpty() -> Artwork(covers.first(), size = 38.dp, corner = 7.dp)
            else -> Icon(Icons.Rounded.QueueMusic, null, Modifier.size(17.dp), tint = Blz.dim)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text, color = Blz.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun Choice(
    name: String,
    detail: String,
    covers: List<String>,
    inside: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        PlaylistArt(covers)
        Column(Modifier.weight(1f)) {
            Text(name, color = Blz.ink, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Blz.dim, fontSize = 11.5.sp)
        }
        if (inside) {
            Icon(Icons.Rounded.Check, "Already in", Modifier.size(16.dp), tint = Blaze.Amber)
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
            .then(
                if (filled) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
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
