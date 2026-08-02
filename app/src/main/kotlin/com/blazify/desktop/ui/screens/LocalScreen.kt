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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Track
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The music already on this machine.
 *
 * Folders are pointed at, not imported — nothing is copied and nothing is
 * moved, so the collection stays exactly where its owner put it. The list is
 * what was found last time, shown straight away, with a rescan a click away.
 */
@Composable
fun LocalScreen(onPlay: (List<Track>, Int) -> Unit, onShuffle: (List<Track>) -> Unit) {
    val scope = rememberCoroutineScope()
    val tracks = LocalMusic.tracks

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("On this computer", color = Blz.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        LocalMusic.scanning -> "Looking through your folders…"
                        tracks.isEmpty() -> "Add a folder and everything in it turns up here"
                        else -> "${tracks.size} songs in ${LocalMusic.folders.size} folders"
                    },
                    color = Blz.muted, fontSize = 13.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tracks.isNotEmpty()) {
                        Pill(Icons.Rounded.PlayArrow, "Play", filled = true) { onPlay(tracks, 0) }
                        Pill(Icons.Rounded.Shuffle, "Shuffle", filled = false) { onShuffle(tracks) }
                    }
                    Pill(Icons.Rounded.CreateNewFolder, "Add folder", filled = tracks.isEmpty()) {
                        chooseFolder()?.let { folder -> scope.launch { LocalMusic.add(folder) } }
                    }
                    if (LocalMusic.folders.isNotEmpty()) {
                        Pill(Icons.Rounded.Refresh, "Rescan", filled = false) {
                            scope.launch { LocalMusic.rescan() }
                        }
                    }
                }
            }
        }

        if (LocalMusic.folders.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "FOLDERS", color = Blz.dim, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                    )
                    LocalMusic.folders.forEach { path -> FolderRow(path) }
                }
            }
        }

        itemsIndexed(tracks, key = { at, track -> "$at-${track.id}" }) { at, track ->
            LocalRow(track) { onPlay(tracks, at) }
        }
    }
}

@Composable
private fun FolderRow(path: String) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Folder, null, Modifier.size(17.dp), tint = Blz.muted)
        Text(
            path, color = Blz.muted, fontSize = 12.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val (removeSource, removeHovered) = rememberHovered()
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(999.dp))
                .hoverBackground(Blz.hover, removeHovered, removeSource)
                .clickable { LocalMusic.remove(path) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Remove folder", Modifier.size(14.dp), tint = Blz.dim)
        }
    }
}

@Composable
private fun LocalRow(track: Track, onPlay: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(Blz.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            // Files carry no artwork here, so the row shows what it does
            // instead of an empty square pretending to be a cover.
            Icon(
                if (hovered.value) Icons.Rounded.PlayArrow else Icons.Rounded.Folder,
                null, Modifier.size(17.dp), tint = if (hovered.value) Blz.ink else Blz.dim,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.title, color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Blz.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember)))
                else Modifier.background(Blz.surface),
            )
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val ink = if (filled) Blaze.OnAmber else Blz.ink
        Icon(icon, label, Modifier.size(19.dp), tint = ink)
        Text(label, color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Ask for a folder, using whatever the desktop's own chooser looks like.
 *
 * The system look is set first: the toolkit's default theme is the same on
 * every platform and matches none of them, which makes the one dialog someone
 * recognises look like it came from another decade.
 */
private fun chooseFolder(): File? = runCatching {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    val chooser = JFileChooser(System.getProperty("user.home")).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choose a music folder"
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}.getOrNull()
