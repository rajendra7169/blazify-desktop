package com.blazify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Link
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
import com.blazify.desktop.data.OwnPlaylist
import com.blazify.desktop.data.PlaylistFiles
import com.blazify.desktop.data.PlaylistImport
import com.blazify.desktop.data.Playlists
import com.blazify.desktop.data.Spotify
import com.blazify.desktop.data.Wanted
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Playlists going in and out: files, and Spotify links.
 *
 * Asked for from wherever the button is — the library, a playlist's own page,
 * the search box, a paste — and drawn over the whole window by the shell, for
 * the same reason the other dialogs are: a row deep in a list has no way to
 * reach the top of the window.
 */
object Transfers {

    /** The paste-a-link dialog, and whatever was already in it. */
    var linkOpen by mutableStateOf(false)
        private set
    var linkText by mutableStateOf("")

    /** One line said after something finished that has no screen of its own. */
    var notice by mutableStateOf<String?>(null)
        private set

    fun openLink(prefill: String = "") {
        linkText = prefill
        linkOpen = true
    }

    fun dismiss() {
        linkOpen = false
        notice = null
    }

    /**
     * Whatever a pasted link turns out to be.
     *
     * A Spotify list is read and imported. Returns false for anything else,
     * so the dialog can say it didn't recognise it.
     */
    fun handle(text: String): Boolean {
        if (Spotify.parse(text) != null) {
            linkOpen = false
            PlaylistImport.fetchThen("Spotify", { Spotify.read(text) }, Spotify::noteFor)
            return true
        }
        return false
    }

    /** Pick a file and import it. */
    fun importFile() {
        val file = chooseOpen() ?: return
        val (name, wanted) = runCatching { PlaylistFiles.read(file) }.getOrElse {
            notice = "Couldn't read ${file.name}"
            return
        }
        PlaylistImport.start(name, wanted)
    }

    /** Write a playlist out, in whichever of the two formats was asked for. */
    fun export(playlist: OwnPlaylist, csv: Boolean) {
        val extension = if (csv) "csv" else "m3u"
        val file = chooseSave("${safe(playlist.name)}.$extension", extension) ?: return
        notice = runCatching {
            file.writeText(if (csv) PlaylistFiles.csv(playlist) else PlaylistFiles.m3u(playlist))
            "Saved ${playlist.tracks.size} songs to ${file.name}"
        }.getOrElse { "Couldn't write ${file.name}" }
    }

    private fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "Playlist" }

    /**
     * The system's own file pickers.
     *
     * Swing's rather than something drawn here: a file is being put somewhere
     * on a real disk, and people find their way round their own machine faster
     * in the picker they already know.
     */
    private fun chooseSave(suggested: String, extension: String): File? {
        val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
            dialogTitle = "Export playlist"
            selectedFile = File(suggested)
        }
        if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
        val picked = chooser.selectedFile ?: return null
        return if (picked.extension.equals(extension, true)) picked else File(picked.parentFile, "${picked.name}.$extension")
    }

    private fun chooseOpen(): File? {
        val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
            dialogTitle = "Import a playlist"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Playlists (M3U, CSV)", "m3u", "m3u8", "csv", "txt",
            )
        }
        if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.takeIf { it.isFile }
    }
}

/** Every dialog above, drawn when it's wanted. */
@Composable
fun PlaylistTransferDialogs() {
    if (Transfers.linkOpen) LinkDialog()
    Transfers.notice?.let { NoticeDialog(it) }
    ImportDialog()
}

/** Paste a link: a Spotify playlist or album, or a playlist somebody shared. */
@Composable
private fun LinkDialog() {
    var trouble by remember { mutableStateOf<String?>(null) }

    Frame(onDismiss = Transfers::dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Import from a link", color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "A Spotify playlist or album.",
                color = Blz.muted, fontSize = 12.5.sp,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Blz.surfaceHigh)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (Transfers.linkText.isEmpty()) {
                Text("Paste a link", color = Blz.dim, fontSize = 13.sp)
            }
            BasicTextField(
                value = Transfers.linkText,
                onValueChange = { Transfers.linkText = it; trouble = null },
                singleLine = true,
                textStyle = TextStyle(color = Blz.ink, fontSize = 13.sp),
                cursorBrush = SolidColor(Blaze.Amber),
                modifier = Modifier.fillMaxWidth().onFocusChanged { Typing.active = it.isFocused },
            )
        }

        trouble?.let { Text(it, color = Blaze.Amber, fontSize = 11.5.sp, lineHeight = 17.sp) }

        Text(
            "Spotify lets anyone not signed in see the first ${Spotify.EMBED_LIMIT} songs of a " +
                "playlist, so a longer one comes across as its first ${Spotify.EMBED_LIMIT}. Each " +
                "song is then looked for here by its title and artist.",
            color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogPill("Import", filled = true, Modifier.weight(1f)) {
                if (!Transfers.handle(Transfers.linkText)) {
                    trouble = "That isn't a link to a Spotify playlist or album."
                }
            }
            DialogPill("Cancel", filled = false, Modifier.weight(1f), Transfers::dismiss)
        }

        Text(
            "Or import an M3U or CSV file",
            color = Blaze.Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { Transfers.dismiss(); Transfers.importFile() }
                .padding(vertical = 4.dp),
        )
    }
}

/**
 * An import, from the moment the list is being read to the count at the end.
 *
 * The count is the point. "41 of 45 songs found" tells somebody exactly how
 * much to trust what they got, and the four that weren't are listed by name
 * so they can be looked for by hand.
 */
@Composable
private fun ImportDialog() {
    val reading = PlaylistImport.reading
    val progress = PlaylistImport.progress
    val outcome = PlaylistImport.outcome
    val problem = PlaylistImport.problem
    if (reading == null && progress == null && outcome == null && problem == null) return

    Frame(onDismiss = { if (outcome != null || problem != null) PlaylistImport.dismiss() }) {
        when {
            problem != null -> {
                Text("Couldn't import that", color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(problem, color = Blz.muted, fontSize = 12.5.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogPill("Close", filled = false, Modifier.weight(1f), PlaylistImport::dismiss)
                }
            }

            outcome != null -> {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(outcome.summary, color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (outcome.playlistId != null) "Made into “${outcome.name}”, on this computer."
                        else "Nothing was made.",
                        color = Blz.muted, fontSize = 12.5.sp,
                    )
                }
                outcome.note?.let { Text(it, color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp) }

                val named = outcome.missing.filter { it.title.isNotBlank() }
                if (named.isNotEmpty()) {
                    Text(
                        "NOT FOUND", color = Blz.dim, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(named) { line ->
                            Text(
                                listOf(line.title, line.artist).filter { it.isNotBlank() }.joinToString("  ·  "),
                                color = Blz.muted, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                } else if (outcome.missing.isNotEmpty()) {
                    Text(
                        "${outcome.missing.size} songs are no longer in the catalogue.",
                        color = Blz.dim, fontSize = 11.5.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    outcome.playlistId?.let { id ->
                        DialogPill("Open it", filled = true, Modifier.weight(1f)) {
                            PlaylistImport.dismiss()
                            Navigator.openPlaylist(id)
                        }
                    }
                    DialogPill("Close", filled = false, Modifier.weight(1f), PlaylistImport::dismiss)
                }
            }

            else -> {
                Text(
                    progress?.let { "Importing ${it.name}" } ?: "Importing",
                    color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    reading ?: progress?.let { "${it.done} of ${it.total} looked for  ·  ${it.found} found" }.orEmpty(),
                    color = Blz.muted, fontSize = 12.5.sp,
                )
                // A bar rather than a spinner: a list of two hundred songs takes
                // a minute, and a minute of spinning is a minute of wondering.
                val fraction = progress?.let { if (it.total == 0) 0f else it.done.toFloat() / it.total } ?: 0f
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(Blz.surfaceHigh)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogPill("Stop", filled = false, Modifier.weight(1f), PlaylistImport::cancel)
                }
            }
        }
    }
}

@Composable
private fun NoticeDialog(text: String) {
    Frame(onDismiss = Transfers::dismiss) {
        Text(text, color = Blz.ink, fontSize = 13.5.sp, lineHeight = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogPill("OK", filled = false, Modifier.weight(1f), Transfers::dismiss)
        }
    }
}

/** The card every one of these sits in, over a dimmed window. */
@Composable
private fun Frame(onDismiss: () -> Unit, width: Int = 380, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Blaze.Scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(width.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blz.bar)
                .clickable(enabled = false) {}
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun DialogPill(
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

/**
 * Share and export, on a playlist's own page.
 *
 * Export opens a short menu rather than being two buttons: the format is a
 * question for the moment of exporting, and two buttons that differ by three
 * letters is a row that reads as noise.
 */
@Composable
fun PlaylistShareActions(playlist: OwnPlaylist) {
    var exporting by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            PageAction(Icons.Rounded.FileDownload, "Export") { exporting = true }
            androidx.compose.material3.DropdownMenu(
                expanded = exporting,
                onDismissRequest = { exporting = false },
                modifier = Modifier.background(Blz.bar),
            ) {
                MenuLine("As M3U, for other players") { exporting = false; Transfers.export(playlist, csv = false) }
                MenuLine("As CSV, for a spreadsheet") { exporting = false; Transfers.export(playlist, csv = true) }
            }
        }
    }
}

/** Import buttons for the library's heading. */
@Composable
fun ImportActions() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PageAction(Icons.Rounded.FileUpload, "Import a file", Transfers::importFile)
        PageAction(Icons.Rounded.Link, "Import from a link") { Transfers.openLink() }
    }
}

@Composable
private fun PageAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Blz.surface)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        androidx.compose.material3.Icon(icon, label, Modifier.size(17.dp), tint = Blz.ink)
        Text(label, color = Blz.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MenuLine(label: String, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .width(240.dp)
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = Blz.ink, fontSize = 13.sp)
    }
}

