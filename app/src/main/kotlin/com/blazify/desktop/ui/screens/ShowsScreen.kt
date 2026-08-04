package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Account
import com.blazify.desktop.data.asTrack
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Mark
import com.blazify.desktop.data.Resume
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.SkeletonRail
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverLift
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.launch

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Programmes, given a page of their own.
 *
 * Not the music page with different contents. A record is a thing you put on;
 * a programme is a thing you are partway through, subscribed to, or picking by
 * subject — so the shapes differ deliberately. What you are in the middle of
 * gets wide cards you can read at a glance, shows get large square artwork
 * because that is the thing recognised, episodes get tall cards with room for
 * a title that is a whole sentence, and the people who make them get circles,
 * which is what a face is.
 *
 * Half of this needs an account and half of it doesn't. The half that doesn't
 * is what greets somebody signed out, rather than an invitation to sign in.
 */
@Composable
fun ShowsScreen(onOpen: (Catalogue.Card) -> Unit) {
    val scope = rememberCoroutineScope()
    val marks = Resume.unfinished
    val following = Library.saved.filter { Catalogue.isShow(it.id) }

    LaunchedEffect(Unit) { ShowsState.ensureLoaded() }
    // Signing in turns half of this page on, and a page that stays empty
    // afterwards reads as a feature that doesn't work.
    LaunchedEffect(Account.signedIn) {
        if (Account.signedIn) {
            ShowsState.forget()
            ShowsState.ensureLoaded()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 26.dp, end = 26.dp, top = 22.dp, bottom = 92.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Podcasts", color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Programmes, interviews and the long ones",
                    color = Blz.muted, fontSize = 13.sp,
                )
            }
        }

        // What somebody is in the middle of, first and widest. It is the only
        // thing on the page that is already theirs.
        if (marks.isNotEmpty()) {
            rail("Carry on") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(marks, key = { it.track.id }) { WideCard(it) }
                }
            }
        }

        if (following.isNotEmpty()) {
            rail("Shows you follow") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(following, key = { it.id }) { ShowTile(it, onOpen) }
                }
            }
        }

        if (ShowsState.fresh.isNotEmpty()) {
            rail("New episodes") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(ShowsState.fresh, key = { at, t -> "fresh-$at-${t.id}" }) { at, track ->
                        TallCard(track) { PlayerState.play(ShowsState.fresh, at, "New episodes") }
                    }
                }
            }
        }

        if (ShowsState.later.isNotEmpty()) {
            rail("Saved for later") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(ShowsState.later, key = { at, t -> "later-$at-${t.id}" }) { at, track ->
                        TallCard(track) { PlayerState.play(ShowsState.later, at, "Saved for later") }
                    }
                }
            }
        }

        if (ShowsState.channels.isNotEmpty()) {
            rail("The people behind them") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(ShowsState.channels, key = { it.id }) { Creator(it, onOpen) }
                }
            }
        }

        // The catalogue's own shelves, when there is an account to ask with.
        items(ShowsState.feed) { shelf ->
            Shelf(shelf, onOpen) { cards, at -> PlayerState.open(cards.getOrNull(at) ?: return@Shelf) }
        }

        // Subjects, which need no account at all and are therefore the part of
        // this page that is always here.
        rail("Browse by subject") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ShowsState.subjects) { subject ->
                    Chip(subject, subject == ShowsState.subject) {
                        scope.launch { ShowsState.choose(subject) }
                    }
                }
            }
        }

        when {
            ShowsState.loadingSubject && ShowsState.subjectShows.isEmpty() -> item { SkeletonRail() }
            ShowsState.subjectShows.isNotEmpty() -> rail("${ShowsState.subject} shows") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(ShowsState.subjectShows, key = { it.id }) { ShowTile(it, onOpen) }
                }
            }
        }

        if (ShowsState.subjectEpisodes.isNotEmpty()) {
            // Converted once for the whole row rather than once per card: every
            // card would otherwise rebuild the entire queue to be able to
            // start it.
            val queue = ShowsState.subjectEpisodes.map { it.asTrack() }
            rail("${ShowsState.subject} episodes") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(queue, key = { at, track -> "ep-$at-${track.id}" }) { at, track ->
                        TallCard(track) { PlayerState.play(queue, at, ShowsState.subject) }
                    }
                }
            }
        }

        if (ShowsState.loading && ShowsState.feed.isEmpty() && marks.isEmpty() && following.isEmpty()) {
            item { SkeletonRail() }
        }

        if (!Account.signedIn) {
            item {
                Text(
                    "Sign in to bring across the shows you follow, the episodes you've saved " +
                        "and the ones out since you last looked.",
                    color = Blz.dim, fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * A heading and the row under it, as one thing.
 *
 * Wrapped rather than left as two children of a list item: what a lazy list
 * does with several children of one item is its business and not worth
 * depending on, and a heading that lands on top of its own row is a bug that
 * only shows up on somebody else's screen.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.rail(
    title: String,
    content: @Composable () -> Unit,
) = item {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Blz.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

/**
 * Something you are partway through, as wide as it needs to be read.
 *
 * Landscape rather than square: the useful thing here is not the artwork, it
 * is which episode this is and how much of it is left, and that is a sentence
 * rather than a picture.
 */
@Composable
private fun WideCard(mark: Mark) {
    val (source, hovered) = rememberHovered()

    Row(
        Modifier
            .width(330.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Blz.surface)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { PlayerState.play(listOf(mark.track), 0, "Carrying on") }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(84.dp)) {
            Artwork(mark.track.thumbnail, size = 84.dp, corner = 9.dp)
            if (hovered.value) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(20.dp), tint = Blaze.OnAmber)
                }
            }
        }
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                mark.track.title, color = Blz.ink, fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                mark.track.artist, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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
            Text(mark.left, color = Blaze.Amber, fontSize = 11.sp)
        }
    }
}

/**
 * A show, at the size its artwork deserves.
 *
 * Bigger than a song tile on purpose: the cover of a programme is how it is
 * recognised, and a hundred and sixty pixels of it is a logo you have to read.
 */
@Composable
private fun ShowTile(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    Column(
        Modifier
            .width(212.dp)
            .clip(RoundedCornerShape(14.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(card.thumbnail, size = 196.dp, corner = 12.dp, modifier = Modifier.hoverLift(hovered))
        Text(
            card.title, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            card.subtitle, color = Blz.muted, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * An episode, tall enough for its own name.
 *
 * Episode titles are sentences — a question, a date, a guest — and cropping
 * one to a line and a half is throwing away the only thing that distinguishes
 * it from the forty others on the same shelf.
 */
@Composable
private fun TallCard(track: Track, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Column(
        Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(164.dp)) {
            Artwork(track.thumbnail, size = 164.dp, corner = 10.dp)
            if (hovered.value) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(22.dp), tint = Blaze.OnAmber)
                }
            }
        }
        Text(
            track.title, color = Blz.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                track.artist, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false),
            )
            if (track.duration.isNotEmpty()) {
                Text(track.duration, color = Blz.dim, fontSize = 11.sp)
            }
        }
    }
}

/** Whoever makes them. A circle, because that is what a face is. */
@Composable
private fun Creator(card: Catalogue.Card, onOpen: (Catalogue.Card) -> Unit) {
    val (source, hovered) = rememberHovered()
    Column(
        Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onOpen(card) }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            card.thumbnail, size = 112.dp, corner = 56.dp,
            modifier = Modifier.clip(CircleShape).hoverLift(hovered),
        )
        Text(
            card.title, color = Blz.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                else Brush.linearGradient(listOf(Blz.surface, Blz.surface)),
            )
            .then(if (on) Modifier else Modifier.hoverBackground(Blz.hover, hovered, source))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (on) Blaze.OnAmber else Blz.muted,
            fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
