package com.blazify.desktop.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.ui.Backdrop
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

private const val WEBSITE = "https://www.rajendrapandey.info.np/"
private const val GITHUB = "https://github.com/rajendra7169"
private const val INSTAGRAM = "https://www.instagram.com/raja.indra7169"
private const val AVATAR = "https://github.com/rajendra7169.png"

/**
 * Who made this.
 *
 * Every other page in this application is about the music. This one is the only
 * place the person behind it appears, which is worth doing properly rather than
 * as a version number and a copyright line — somebody who has been using a
 * thing for months is entitled to know whose work it is.
 */
@Composable
fun AboutSection(
    section: @Composable (String, (() -> Unit)?, @Composable () -> Unit) -> Unit,
) {
    var coffee by remember { mutableStateOf(false) }

    section("The person who made it", null) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Fetched from the account rather than bundled, so a changed
                // photograph changes here too without a new release.
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Blz.surfaceHigh),
                ) {
                    Backdrop(AVATAR, Modifier.fillMaxSize())
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Rajendra Pandey", color = Blz.ink, fontSize = 24.sp,
                        fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp,
                    )
                    Text("Developer", color = Blaze.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Social(Icons.Rounded.Language, "Website", Modifier.weight(1f)) { open(WEBSITE) }
                Social(Icons.Rounded.Code, "GitHub", Modifier.weight(1f)) { open(GITHUB) }
                Social(Icons.Rounded.PhotoCamera, "Instagram", Modifier.weight(1f)) { open(INSTAGRAM) }
            }

            Text(
                "I'm Rajendra Pandey, an independent app developer from Nepal who loves " +
                    "turning ideas into polished, everyday experiences. Blazify is my take " +
                    "on music streaming — fast, beautiful and personal — crafted from the " +
                    "ground up with Kotlin and Compose. I care about the little details " +
                    "that make an app feel effortless, and I'm always tinkering to make it " +
                    "better. Thanks for being here and letting my work be part of your day.",
                color = Blz.muted, fontSize = 13.sp, lineHeight = 21.sp,
            )

            Wide("Buy me a coffee", Icons.Rounded.Coffee) { coffee = true }

            Text(
                "Made with ❤️ by Rajendra Pandey",
                color = Blz.dim, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }

    if (coffee) CoffeeDialog { coffee = false }
}

/**
 * The code to scan, at a size a phone camera can actually read.
 *
 * Drawn on white whatever the theme is doing. A QR code inverted for a dark
 * background is a QR code that half the scanners in the world will refuse, and
 * a support button that doesn't work is worse than none.
 */
@Composable
private fun CoffeeDialog(onDismiss: () -> Unit) {
    val code = remember {
        runCatching { useResource("coffee_qr.png") { loadImageBitmap(it) } }.getOrNull()
    }

    Box(
        Modifier.fillMaxSize().background(Blaze.Scrim).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Buy me a coffee", color = Blz.ink, fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Scan the code to support Blazify",
                color = Blz.muted, fontSize = 12.5.sp,
            )
            code?.let {
                Box(
                    Modifier
                        .size(232.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(10.dp),
                ) {
                    Image(it, "Support QR code", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Wide("Close", null, onClick = onDismiss)
        }
    }
}

@Composable
private fun Social(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Blz.surfaceHigh)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, Modifier.size(17.dp), tint = Blz.muted)
        Text(
            label, color = Blz.ink, fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Wide(label: String, icon: ImageVector?, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
            .hoverGlow(hovered, source)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(18.dp), tint = Blaze.OnAmber)
        }
        Text(
            label, color = Blaze.OnAmber, fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = if (icon != null) 9.dp else 0.dp),
        )
    }
}

/** Hand a link to whatever browser this machine uses. */
private fun open(url: String) {
    runCatching {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)
        if (windows) ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
        else ProcessBuilder("xdg-open", url).start()
    }
}
