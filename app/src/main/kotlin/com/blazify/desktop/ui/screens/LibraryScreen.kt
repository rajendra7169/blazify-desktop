package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.OwnPlaylist
import com.blazify.desktop.data.Playlists
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.items
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Skeleton
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.EmptyState
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everything you kept.
 *
 * Saved albums and playlists in a grid that grows down the page rather than
 * sideways — this is the one screen where you're looking *for* something, and a
 * rail that hides two thirds of itself is the wrong shape for that.
 */
@Composable
fun LibraryScreen(onOpen: (Catalogue.Card) -> Unit, onOpenPlaylist: (String) -> Unit) {
    val saved = Library.saved
    val own = Playlists.all
    var mine by remember { mutableStateOf<List<Catalogue.Card>>(emptyList()) }
    var shows by remember { mutableStateOf<List<Catalogue.Card>>(emptyList()) }
    // True until the first answer arrives, whatever the account is doing.
    //
    // It used to start at `Account.signedIn`, which is false for the second or
    // two the stored session takes to be checked — so the page opened by saying
    // the library was empty and then filled in behind that. "Nothing here" is a
    // statement of fact and it has to wait until it is one.
    var asked by remember { mutableStateOf(false) }

    // Re-asked whenever you sign in or out, since the answer is entirely
    // different on each side of that, and once the check itself finishes.
    LaunchedEffect(Account.cookie, Account.checking) {
        if (Account.checking) return@LaunchedEffect
        mine = Catalogue.mine().getOrDefault(emptyList())
        // A second request, because the catalogue files shows somewhere else
        // entirely and the one that returns everything else returns none of
        // them.
        shows = Catalogue.myShows()
        asked = true
    }

    val loading = !asked

    // Shows kept here and shows kept on the account are the same thing arrived
    // at two ways, and a page that lists them twice is a page that looks broken.
    val allShows = (shows + saved.filter { Catalogue.isShow(it.id) })
        .distinctBy { it.id }
    val records = saved.filterNot { Catalogue.isShow(it.id) }

    // A session that has lapsed is not the same as never having had one, and
    // telling someone to sign in when they already did is how an expired
    // cookie turns into "the app lost my playlists".
    val lapsed = Account.hasCredential && !Account.signedIn && !Account.checking

    // Shelves in outline while the account is being asked. A grid that appears
    // where an empty page was is the app correcting itself; a grid that fills
    // in where its own outline was is the app loading.
    if (loading && saved.isEmpty() && own.isEmpty()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(190.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(8) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
                    Skeleton(Modifier.fillMaxWidth(0.75f).height(11.dp))
                    Skeleton(Modifier.fillMaxWidth(0.45f).height(9.dp))
                }
            }
        }
        return
    }

    if (saved.isEmpty() && mine.isEmpty() && own.isEmpty() && allShows.isEmpty() && !loading) {
        EmptyState(
            Icons.Rounded.LibraryMusic,
            if (lapsed) "Your session has expired" else "Your library is empty",
            when {
                lapsed ->
                    "Your playlists are still on your account — this computer's sign-in has " +
                        "lapsed, which YouTube does every few weeks. Settings › Account › " +
                        "Signing in again puts it back."
                Account.signedIn ->
                    "Save an album or playlist and it collects here, alongside anything you make."
                else ->
                    "Sign in to bring your own playlists across, or save anything you find to keep it here."
            },
        )
        return
    }

    // Said above the grid too, or someone with one local playlist sees that
    // one and concludes the other twenty are gone.
    if (lapsed) {
        Text(
            "Signed out — your account's playlists aren't showing. " +
                "Settings › Account › Sign in.",
            color = Blaze.Amber, fontSize = 12.sp,
            modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 16.dp),
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(190.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Library", color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        own.size.takeIf { it > 0 }?.let { "$it made here" },
                        mine.size.takeIf { it > 0 }?.let { "$it yours" },
                        allShows.size.takeIf { it > 0 }?.let { "$it shows" },
                        records.size.takeIf { it > 0 }?.let { "$it saved" },
                    ).joinToString("  ·  ").ifEmpty { "Nothing here yet" },
                    color = Blz.muted, fontSize = 13.sp,
                )
            }
        }

        // Made here first: someone assembled these song by song, which outranks
        // both what an account holds and anything picked up along the way.
        if (own.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Heading("Made here") }
            itemsIndexed(own, key = { at, it -> "own-$at-${it.id}" }) { _, playlist ->
                OwnTile(playlist, onOpenPlaylist)
            }
        }

        // What's on the account next: those are playlists someone built, and
        // they outrank anything picked up along the way.
        if (mine.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Heading("Your playlists") }
            itemsIndexed(mine, key = { at, it -> "mine-$at-${it.id}" }) { _, card -> SavedTile(card, onOpen) }
        }

        // Shows of their own, before the general pile. A podcast among albums
        // is a podcast nobody finds again: it looks like a record, it is filed
        // like a record, and the one thing it is not is a record.
        if (allShows.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Heading("Shows") }
            itemsIndexed(allShows, key = { at, it -> "show-$at-${it.id}" }) { _, card ->
                SavedTile(card, onOpen)
            }
        }

        if (records.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Heading("Saved") }
            itemsIndexed(records, key = { at, it -> "saved-$at-${it.id}" }) { _, card -> SavedTile(card, onOpen) }
        }
    }
}

/**
 * A playlist made here.
 *
 * It has no artwork of its own, so the first four covers in it stand in — the
 * songs are the only honest thing to show, and a collage says "several songs"
 * at a glance in a way a single cover doesn't.
 */
@Composable
private fun OwnTile(playlist: OwnPlaylist, onOpen: (String) -> Unit) {
    val (source, hovered) = rememberHovered()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(playlist.id) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(168.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Blz.surfaceHigh)
                .hoverLift(hovered),
            contentAlignment = Alignment.Center,
        ) {
            val covers = playlist.covers
            when {
                covers.size >= 4 -> Column {
                    androidx.compose.foundation.layout.Row {
                        Artwork(covers[0], size = 84.dp, corner = 0.dp)
                        Artwork(covers[1], size = 84.dp, corner = 0.dp)
                    }
                    androidx.compose.foundation.layout.Row {
                        Artwork(covers[2], size = 84.dp, corner = 0.dp)
                        Artwork(covers[3], size = 84.dp, corner = 0.dp)
                    }
                }
                covers.isNotEmpty() -> Artwork(covers.first(), size = 168.dp, corner = 0.dp)
                else -> Icon(
                    Icons.Rounded.QueueMusic, null, Modifier.size(34.dp), tint = Blz.dim,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                playlist.name, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text("${playlist.tracks.size} songs", color = Blz.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(), color = Blz.dim, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SavedTile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    val round = card.kind == Catalogue.Kind.Artist

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Artwork(
            card.thumbnail,
            size = 168.dp,
            corner = if (round) 84.dp else 12.dp,
            modifier = Modifier.hoverLift(hovered)
                .then(if (round) Modifier.clip(CircleShape) else Modifier),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                card.title, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
            )
            Text(
                card.subtitle, color = Blz.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}
