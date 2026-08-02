package com.blazify.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.blazify.desktop.data.Account
import java.time.LocalTime

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The card that opens the home screen.
 *
 * A gradient card with
 * the hours of the day on the left and the hero image spilling out over the top
 * edge on the right. Someone moving between the two should recognise this
 * immediately — that recognition is most of what makes them feel like one
 * application rather than two that share a name.
 *
 * The gradient runs from the accent to a darkened version of itself, so on a
 * window that follows the artwork this card changes with the music too.
 */
@Composable
fun GreetingCard(modifier: Modifier = Modifier) {
    val start = Blaze.Amber
    val end = lerp(start, Color.Black, if (Blz.dark) 0.30f else 0.20f)

    val onCard = Blaze.OnAmber

    // Read once and held: it's a megabyte of picture and the screen it sits on
    // rebuilds every time a shelf arrives.
    val dark = Blz.dark
    val hero = remember(dark) {
        val name = if (dark) "blaze_home_dark.png" else "blaze_home_light.png"
        runCatching { useResource(name) { loadImageBitmap(it) } }.getOrNull()
    }

    Box(modifier.fillMaxWidth().height(196.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(start, end))),
        )

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.62f)
                .padding(start = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                greeting(),
                color = onCard,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                lineHeight = 35.sp,
            )
            Text(
                // Whoever is signed in, or nobody in particular.
                Account.name ?: "there",
                color = onCard.copy(alpha = 0.95f),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Enjoy the music 🎵",
                color = onCard.copy(alpha = 0.85f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        hero?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 34.dp)
                    // Sized for a window rather than a panel. The card is wider
                    // here, and a small figure at one end of it leaves the
                    // middle looking like something failed to load.
                    .requiredWidth(300.dp)
                    .requiredHeight(360.dp)
                    // A required height overflows evenly at both ends; shifting
                    // up by half of it puts the bottom edge flush with the card
                    // and leaves the rest standing above it.
                    .offset(y = (-82).dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

/** Split across two lines. */
private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good\nMorning 🌅"
    in 12..16 -> "Good\nAfternoon ☀️"
    in 17..20 -> "Good\nEvening 🌆"
    else -> "Good\nNight 🌙"
}
