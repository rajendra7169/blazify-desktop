package com.blazify.desktop.data

import java.util.Base64

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * A playlist sent as a link.
 *
 * Everything the playlist is travels in the link itself — its name, and the
 * eleven-character id of every song in order — so there is no server keeping
 * a copy and nothing to sign up to. The part after the # never leaves the
 * browser of whoever opens it, which makes it private by the way the web works
 * rather than by promise.
 *
 * The leading "1." is the format's version, so a later change can be told
 * apart from this one by any copy of the app that meets it.
 */
object SharedPlaylists {

    const val BASE = "https://rajendra7169.github.io/blazify/playlist#"
    private const val FORMAT = "1"

    /**
     * The most songs a link carries.
     *
     * A hundred and fifty is about seventeen hundred characters: long for a
     * link, but every chat and mail program passes it whole, and it still
     * makes a QR code a camera reads from a screen.
     */
    const val MOST = 150

    /**
     * A link made, and how much of the playlist made it in: songs past the
     * limit, and songs that can't travel at all, counted separately because
     * they are different things to tell somebody.
     */
    data class Link(val url: String, val shared: Int, val overLimit: Int, val unshareable: Int)

    /** A link read back. */
    data class Shared(val name: String, val ids: List<String>)

    /**
     * The link for a list of songs.
     *
     * Only songs from the catalogue can travel. A file on this computer is on
     * nobody else's, and a programme from a feed has no id the other end
     * could look up — so those are counted out rather than sent as dead ends.
     */
    fun link(name: String, tracks: List<Track>): Link {
        val shareable = tracks.map { it.id }.filter { ID.matches(it) }.distinct()
        val unshareable = tracks.count { !ID.matches(it.id) }
        val ids = shareable.take(MOST)
        val title = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(name.trim().take(80).toByteArray(Charsets.UTF_8))
        return Link(
            url = "$BASE$FORMAT.$title.${ids.joinToString("")}",
            shared = ids.size,
            overLimit = shareable.size - ids.size,
            unshareable = unshareable,
        )
    }

    /**
     * The playlist in a pasted link, if it is one of these.
     *
     * Looked for anywhere in the text, because people paste a link with a
     * line of their own around it.
     */
    fun parse(text: String): Shared? {
        val at = text.indexOf(MARK)
        if (at < 0) return null
        val body = text.substring(at + MARK.length).trim().takeWhile { !it.isWhitespace() }
        val parts = body.split('.')
        if (parts.size < 3 || parts[0] != FORMAT) return null

        val name = runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8).trim()
        }.getOrNull() ?: return null
        val ids = parts[2].chunked(11).filter { ID.matches(it) }
        if (ids.isEmpty()) return null
        return Shared(name.ifBlank { "Shared playlist" }, ids.take(MOST))
    }

    /** What every one of these links contains, whoever's copy of the address. */
    private const val MARK = "/blazify/playlist#"

    private val ID = Regex("[A-Za-z0-9_-]{11}")
}
