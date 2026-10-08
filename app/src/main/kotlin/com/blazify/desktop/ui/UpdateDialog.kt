package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.UpdateInstall
import com.blazify.desktop.data.Updates

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The update, from the first byte to the restart, in one place.
 *
 * It opens when the download starts and shows how far it has got; when the file
 * is whole the same box offers to install it. A download that stops says why
 * and offers to try again, carrying on from where it stopped.
 */
@Composable
fun UpdateDialog() {
    if (!Updates.showing) return

    val progress = Updates.progress
    val ready = Updates.ready != null
    val failure = Updates.failure
    val version = Updates.latest.orEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.Scrim)
            // Clicking beside it only puts it away: a download carries on, and a finished one waits.
            .clickable(onClick = Updates::hide),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                when {
                    ready -> "Blazify $version is ready"
                    failure != null -> "The download stopped"
                    else -> "Downloading Blazify $version"
                },
                color = Blz.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            when {
                ready ->
                    Text(
                        when (UpdateInstall.kind) {
                            UpdateInstall.Kind.Deb ->
                                "Blazify will close, ask for your password in the system's own " +
                                    "window, install the new version and open again."
                            UpdateInstall.Kind.AppImage ->
                                "Blazify will close, swap in the new version and open again."
                            UpdateInstall.Kind.WindowsSetup ->
                                "Blazify will close and the setup for the new version will open. " +
                                    "Your library and settings stay as they are."
                            UpdateInstall.Kind.Unknown -> "The new version has been downloaded."
                        },
                        color = Blz.dim,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )

                failure != null ->
                    Text(
                        "$failure. Trying again carries on from where it stopped.",
                        color = Blz.dim,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )

                else -> {
                    val shown = progress ?: 0f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(shown)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Blaze.Amber),
                        )
                    }
                    Text(
                        "${(shown * 100).toInt()}%  ·  " + megabytes(Updates.received) +
                            (Updates.total?.let { " of " + megabytes(it) } ?: ""),
                        color = Blz.muted,
                        fontSize = 12.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    ready -> {
                        DialogAction("Later", Blz.muted, onClick = Updates::hide)
                        DialogAction("Install and restart", Blaze.Amber, bold = true, onClick = Updates::install)
                    }
                    failure != null -> {
                        DialogAction("Close", Blz.muted, onClick = Updates::hide)
                        DialogAction("Try again", Blaze.Amber, bold = true, onClick = Updates::fetch)
                    }
                    else -> {
                        DialogAction("Cancel", Blz.muted, onClick = Updates::cancel)
                        DialogAction("Hide", Blz.muted, onClick = Updates::hide)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    label: String,
    color: Color,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun megabytes(bytes: Long) = "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_000_000.0)
