package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.Plays
import com.blazify.desktop.data.Track
import com.blazify.desktop.data.Wrapped
import com.blazify.desktop.ui.Artwork
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.EmptyState
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Your year in music.
 *
 * The top lists answer "what lately"; this answers "what was this year", as a
 * handful of cards rather than another list — the time, the songs, the people,
 * the month it all happened in. Everything is counted on this computer from
 * plays already kept here; nothing is sent anywhere to build it.
 */
@Composable
fun WrappedScreen(
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val years = remember(Plays.all) { Wrapped.years() }
    var year by remember { mutableStateOf(Wrapped.defaultYear()) }
    val known = remember(Library.history, Library.liked) { Library.known() }
    val wrap = remember(year, Plays.all, known) { Wrapped.of(year, known = known) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 26.dp, top = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val (source, hovered) = rememberHovered()
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .hoverBackground(Blz.hover, hovered, source)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, "Back", Modifier.size(19.dp), tint = Blz.muted)
            }
            Column(Modifier.weight(1f)) {
                Text("Your year in music", color = Blz.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(Wrapped.caption(year), color = Blz.muted, fontSize = 12.5.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.take(4).forEach { option ->
                    Chip("$option", option == year) { year = option }
                }
            }
        }

        if (wrap.plays == 0) {
            EmptyState(
                Icons.Rounded.AutoAwesome,
                "Nothing counted in $year",
                "Every song you play here is counted, and this page adds them up into " +
                    "your year: the time, the songs, the artists and your busiest month.",
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Hero(wrap) }

            item {
                Row(
                    Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Figure("Different songs", "${wrap.songs}", Modifier.weight(1f))
                    Figure("Different artists", "${wrap.artists}", Modifier.weight(1f))
                    Figure("Plays", "${wrap.plays}", Modifier.weight(1f))
                }
            }

            item {
                Row(
                    Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Card("Top songs", Modifier.weight(1f)) {
                        if (wrap.topSongs.isEmpty()) Quiet("The songs played this year can't be found any more.")
                        wrap.topSongs.forEachIndexed { at, (track, count) ->
                            Ranked(at + 1, track.thumbnail, track.title, track.artist, count, round = false) {
                                onPlay(wrap.topSongs.map { it.first }, at)
                            }
                        }
                    }
                    Card("Top artists", Modifier.weight(1f)) {
                        if (wrap.topArtists.isEmpty()) Quiet("No artist names were kept for this year's songs.")
                        wrap.topArtists.forEachIndexed { at, tally ->
                            Ranked(
                                at + 1, tally.thumbnail, tally.name,
                                if (tally.songs == 1) "1 song" else "${tally.songs} songs",
                                tally.plays, round = true,
                            ) {
                                val theirs = wrap.topSongs.map { it.first }
                                    .filter { it.artist.substringBefore(",").trim() == tally.name }
                                if (theirs.isNotEmpty()) onPlay(theirs, 0)
                            }
                        }
                    }
                }
            }

            item { MonthsCard(wrap) }
        }
    }
}

/** The headline: how long, in the unit that makes the number feel like something. */
@Composable
private fun Hero(wrap: Wrapped.Year) {
    val minutes = wrap.seconds / 60
    Column(
        Modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${wrap.year}", color = Blaze.OnAmber.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "%,d minutes".format(minutes),
            color = Blaze.OnAmber, fontSize = 44.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            when {
                minutes >= 60 * 24 -> "of music — about ${minutes / (60 * 24)} " +
                    "${if (minutes / (60 * 24) == 1L) "day" else "days"} without stopping"
                minutes >= 60 -> "of music — about ${minutes / 60} ${if (minutes / 60 == 1L) "hour" else "hours"}"
                else -> "of music, and the year has only just begun"
            },
            color = Blaze.OnAmber.copy(alpha = 0.85f), fontSize = 14.sp,
        )
        wrap.topSongs.firstOrNull()?.let { (track, count) ->
            Text(
                "Your song of the year: ${track.title}, $count plays",
                color = Blaze.OnAmber.copy(alpha = 0.85f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Blz.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = Blz.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Blz.muted, fontSize = 12.5.sp)
    }
}

@Composable
private fun Card(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Blz.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title.uppercase(), color = Blz.dim, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, color = Blz.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun Ranked(
    place: Int,
    picture: String?,
    title: String,
    detail: String,
    plays: Int,
    round: Boolean,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$place",
            color = if (place == 1) Blaze.Amber else Blz.dim,
            fontSize = if (place == 1) 16.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp),
        )
        Artwork(picture, size = 40.dp, corner = if (round) 20.dp else 6.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Blz.ink, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Blz.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("$plays", color = Blz.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The year as twelve bars, with the busiest one lit.
 *
 * Named in words as well as drawn, because "March" is the answer somebody
 * remembers and the bars are only there to show how far ahead it was.
 */
@Composable
private fun MonthsCard(wrap: Wrapped.Year) {
    val most = (wrap.months.maxOrNull() ?: 0).coerceAtLeast(1)
    val best = wrap.bestMonth
    Card("Your busiest month", Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
        if (best != null) {
            Text(
                Wrapped.monthName(best.first),
                color = Blz.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                "${best.second} plays",
                color = Blz.muted, fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            wrap.months.forEachIndexed { at, count ->
                val lit = best != null && best.first.value == at + 1
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight((count.toFloat() / most).coerceIn(0.03f, 1f))
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(
                                if (lit) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                                else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
                            ),
                    )
                    Text(
                        Wrapped.monthName(java.time.Month.of(at + 1)).take(3),
                        color = if (lit) Blaze.Amber else Blz.dim, fontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (on) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surfaceHigh),
            )
            .then(
                if (on) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (on) Blaze.OnAmber else Blz.muted,
            fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
