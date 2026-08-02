package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.PlayerState
import com.blazify.desktop.data.Catalogue
import com.blazify.desktop.data.Downloads
import com.blazify.desktop.data.Library
import com.blazify.desktop.data.LocalMusic
import com.blazify.desktop.data.Track
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everything you can do to one song.
 *
 * The same list wherever a song appears, in the same order, because "what can I
 * do with this" shouldn't depend on which screen you found it on. It opens from
 * the button on the row and from a right-click, and both show the same thing —
 * one is discoverable and the other is faster, and neither should be a
 * different menu.
 *
 * Items that would do nothing aren't shown rather than shown greyed out: there
 * is no album to go to for a local file, and offering it teaches people the
 * menu is decorative.
 */
@Composable
fun SongSheet(
    track: Track,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val liked = Library.isLiked(track.id)
    val kept = Downloads.has(track.id)
    val local = LocalMusic.isLocal(track.id)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(268.dp).background(Blz.bar),
    ) {
        // What you're acting on, at the top. A menu of eleven verbs with no
        // subject is a menu you have to remember your way into.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Artwork(track.thumbnail, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    track.title, color = Blz.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist, color = Blz.muted, fontSize = 11.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Divider()

        if (!local) {
            Item(Icons.Rounded.Radio, "Start radio") {
                PlayerState.startRadio(track); onDismiss()
            }
        }
        Item(Icons.Rounded.PlaylistPlay, "Play next") { PlayerState.playNext(track); onDismiss() }
        Item(Icons.Rounded.QueueMusic, "Add to queue") { PlayerState.addToQueue(track); onDismiss() }
        Item(Icons.Rounded.PlaylistAdd, "Add to playlist…") {
            Dialogs.addToPlaylist(track); onDismiss()
        }

        Divider()

        Item(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (liked) "Remove from liked songs" else "Like",
            tint = if (liked) Blaze.Amber else null,
        ) { Library.toggleLike(track); onDismiss() }

        if (!local) {
            Item(
                if (kept) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                if (kept) "Remove download" else "Keep for offline",
                tint = if (kept) Blaze.Amber else null,
            ) {
                if (kept) Downloads.remove(track.id) else Downloads.start(track)
                onDismiss()
            }
        }

        if (track.artistId != null || track.albumId != null) {
            Divider()
            track.artistId?.let { id ->
                Item(Icons.Rounded.Person, "Go to artist") {
                    Navigator.open(
                        Catalogue.Card(id, track.artist, "Artist", track.thumbnail, Catalogue.Kind.Artist),
                    )
                    onDismiss()
                }
            }
            track.albumId?.let { id ->
                Item(Icons.Rounded.Album, "Go to album") {
                    Navigator.open(
                        Catalogue.Card(id, track.title, track.artist, track.thumbnail, Catalogue.Kind.Album),
                    )
                    onDismiss()
                }
            }
        }

        if (!local) {
            Divider()
            // The desktop equivalent of sharing: there is no share sheet to
            // hand it to, so it goes where anything else would be pasted from.
            Item(Icons.Rounded.Link, "Copy link") {
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        StringSelection("https://music.youtube.com/watch?v=${track.id}"),
                        null,
                    )
                }
                onDismiss()
            }
        }

        onRemove?.let {
            Divider()
            Item(Icons.Rounded.Delete, "Remove from this list") { it(); onDismiss() }
        }
    }
}

/**
 * The button that opens it.
 *
 * Faint until the row is pointed at, then plain. A column of dots down every
 * list is noise; a dot that appears where the pointer is, isn't.
 */
@Composable
fun SongSheetButton(
    track: Track,
    hovered: Boolean,
    onRemove: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val (source, over) = rememberHovered()

    Box {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .hoverBackground(Blz.hover, over, source)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                "More",
                Modifier.size(18.dp),
                tint = if (hovered || open) Blz.ink else Blz.dim.copy(alpha = 0.35f),
            )
        }
        SongSheet(track, open, { open = false }, onRemove)
    }
}

@Composable
private fun Item(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint ?: Blz.muted)
        Text(label, color = tint ?: Blz.ink, fontSize = 13.sp)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp).background(Blz.line))
}
