package com.blazify.desktop.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.blazify.desktop.data.QrCode
import com.blazify.desktop.data.SharedPlaylists
import com.blazify.desktop.data.Spotify
import com.blazify.desktop.data.Wanted
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Playlists going in and out: files, Spotify links, and links to share.
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

    /** The playlist being shared, by id. */
    var sharing by mutableStateOf<String?>(null)
        private set

    /** A shared playlist somebody pasted, waiting on a yes or a no. */
    var offered by mutableStateOf<SharedPlaylists.Shared?>(null)
        private set

    /** One line said after something finished that has no screen of its own. */
    var notice by mutableStateOf<String?>(null)
        private set

    fun openLink(prefill: String = "") {
        linkText = prefill
        linkOpen = true
    }

    fun share(playlist: OwnPlaylist) { sharing = playlist.id }

    fun offer(shared: SharedPlaylists.Shared) { offered = shared }

    fun dismiss() {
        linkOpen = false
        sharing = null
        offered = null
        notice = null
    }

    /**
     * Whatever a pasted link turns out to be.
     *
     * A shared playlist is offered to keep; a Spotify list is read and
     * imported. Returns false for anything else, so the paste can go on to
     * mean whatever it meant before.
     */
    fun handle(text: String): Boolean {
        SharedPlaylists.parse(text)?.let { offer(it); linkOpen = false; return true }
        if (Spotify.parse(text) != null) {
            linkOpen = false
            PlaylistImport.fetchThen("Spotify", { Spotify.read(text) }, Spotify::noteFor)
            return true
        }
        return false
    }

    /** A paste outside any text field: taken if it is one of the links above. */
    fun pasted(): Boolean {
        val text = runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull() ?: return false
        return handle(text)
    }

    /** Keep a shared playlist: its songs are fetched by id and made a playlist here. */
    fun keep(shared: SharedPlaylists.Shared) {
        offered = null
        PlaylistImport.start(shared.name, shared.ids.map { Wanted(title = "", id = it) })
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
    Transfers.sharing?.let { id ->
        Playlists.find(id)?.let { ShareDialog(it) } ?: Transfers.dismiss()
    }
    Transfers.offered?.let { OfferDialog(it) }
    if (Transfers.linkOpen) LinkDialog()
    Transfers.notice?.let { NoticeDialog(it) }
    ImportDialog()
}

/**
 * A playlist as a link and as a code.
 *
 * The code is for the phone on the desk — pointed at the screen, it opens the
 * same link without anybody typing seventeen hundred characters.
 */
@Composable
private fun ShareDialog(playlist: OwnPlaylist) {
    val link = remember(playlist) { SharedPlaylists.link(playlist.name, playlist.tracks) }
    val code = remember(link.url) { QrCode.encode(link.url) }
    var copied by remember(link.url) { mutableStateOf(false) }

    Frame(onDismiss = Transfers::dismiss, width = 420) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Share playlist", color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(playlist.name, color = Blz.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (link.shared == 0) {
            Text(
                "Nothing in this playlist can travel as a link — songs on this computer and " +
                    "programmes from feeds aren't anywhere the other end could find them.",
                color = Blz.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogPill("Close", filled = false, Modifier.weight(1f), Transfers::dismiss)
            }
            return@Frame
        }

        code?.let {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrImage(it) }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Blz.surfaceHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                link.url, color = Blz.muted, fontSize = 11.5.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            buildString {
                append("${link.shared} songs in the link.")
                if (link.overLimit > 0) {
                    append(" A link holds ${SharedPlaylists.MOST}, so the last ${link.overLimit} are left out.")
                }
                if (link.unshareable > 0) {
                    append(" ${link.unshareable} left out because they are files on this computer or programmes from feeds.")
                }
                append(" Anyone with Blazify can open it and keep a copy.")
            },
            color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogPill(if (copied) "Copied" else "Copy link", filled = true, Modifier.weight(1f)) {
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link.url), null)
                    copied = true
                }
            }
            DialogPill("Done", filled = false, Modifier.weight(1f), Transfers::dismiss)
        }
    }
}

/**
 * A QR code, drawn dark on white whatever the theme.
 *
 * Inverted codes are part of the standard but not every camera app reads
 * them, and the white margin around it is part of the code, not decoration.
 */
@Composable
private fun QrImage(code: QrCode) {
    val quiet = 4
    val modules = code.size + quiet * 2
    Canvas(
        Modifier
            .size(300.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White),
    ) {
        val cell = size.minDimension / modules
        val origin = (size.minDimension - cell * modules) / 2 + cell * quiet
        for (y in 0 until code.size) {
            var x = 0
            while (x < code.size) {
                if (!code[x, y]) { x++; continue }
                // One rectangle per run of dark rather than per module: the
                // largest code is twenty thousand of them otherwise, and hairline
                // seams appear between neighbours at fractional sizes.
                val start = x
                while (x < code.size && code[x, y]) x++
                drawRect(
                    Color.Black,
                    topLeft = Offset(origin + start * cell, origin + y * cell),
                    size = Size(cell * (x - start) + 0.5f, cell + 0.5f),
                )
            }
        }
    }
}

/** A shared playlist, offered. Nothing is kept until it's asked for. */
@Composable
private fun OfferDialog(shared: SharedPlaylists.Shared) {
    Frame(onDismiss = Transfers::dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("A shared playlist", color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "${shared.name}  ·  ${shared.ids.size} songs",
                color = Blz.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Keep it, and it is made a playlist on this computer, yours to change.",
            color = Blz.dim, fontSize = 12.5.sp, lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogPill("Keep it", filled = true, Modifier.weight(1f)) { Transfers.keep(shared) }
            DialogPill("Not now", filled = false, Modifier.weight(1f), Transfers::dismiss)
        }
    }
}

/** Paste a link: a Spotify playlist or album, or a playlist somebody shared. */
@Composable
private fun LinkDialog() {
    var trouble by remember { mutableStateOf<String?>(null) }

    Frame(onDismiss = Transfers::dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Import from a link", color = Blz.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "A Spotify playlist or album, or a playlist shared from Blazify.",
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
                    trouble = "That isn't a link to a Spotify playlist or album, or a shared playlist."
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
        PageAction(Icons.Rounded.Share, "Share") { Transfers.share(playlist) }
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

/** Whether some text is a link to a playlist this app can take in. */
fun isPlaylistLink(text: String): Boolean =
    SharedPlaylists.parse(text) != null || Spotify.parse(text) != null

/**
 * A playlist link found in the search box, offered where the results would be.
 *
 * Said plainly what it is and what keeping it does, with the one button —
 * the box was being used as a place to paste, and the answer to a paste is
 * the thing pasted.
 */
@Composable
fun PlaylistLinkFound(text: String) {
    val shared = remember(text) { SharedPlaylists.parse(text) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Blz.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (shared != null) "A shared playlist" else "A Spotify playlist",
                color = Blz.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (shared != null) "${shared.name}  ·  ${shared.ids.size} songs"
                else "Its songs are looked for here and made into a playlist on this computer.",
                color = Blz.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        DialogPill(
            if (shared != null) "Keep it" else "Import it",
            filled = true,
            Modifier.width(120.dp),
        ) {
            if (shared != null) Transfers.keep(shared) else Transfers.handle(text)
        }
    }
}
