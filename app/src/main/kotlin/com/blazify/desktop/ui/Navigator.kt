package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.ui.screens.SettingsPage
import com.blazify.desktop.data.Catalogue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Where you are, and how you got there.
 *
 * The rail picks a destination; opening a playlist or an artist from inside one
 * pushes on top of it. Going back unwinds that pile one at a time and lands you
 * where you started, so following three albums deep isn't a one-way trip. The
 * rail always resets the pile — choosing Home means Home, not Home with an
 * album still stacked over it.
 */
object Navigator {

    // Wherever you asked to land. Someone who lives in their own library
    // shouldn't have to walk past the feed every time they open the app.
    var destination by mutableStateOf(Look.startTab)
        private set

    private val stack = mutableStateListOf<Catalogue.Card>()

    /** The collection being looked at, if any. */
    val opened: Catalogue.Card? get() = stack.lastOrNull()

    val canGoBack: Boolean get() = stack.isNotEmpty()

    /**
     * Whether the settings are showing.
     *
     * Kept apart from the rail rather than added to it: settings are somewhere
     * you visit and leave, not one of the places you listen from, and putting
     * them in the list would push the music one row further down forever.
     */
    var settingsOpen by mutableStateOf(false)
        private set

    /** Which page the settings should land on when they open. */
    var settingsPage by mutableStateOf(SettingsPage.Account)
        private set

    fun openSettings(page: SettingsPage = SettingsPage.Account) {
        settingsPage = page
        settingsOpen = true
        stack.clear()
        playlist = null
    }

    /**
     * Leave the settings.
     *
     * The destination underneath is untouched, so this puts you back on
     * whatever you were listening from rather than at the top of the app.
     */
    fun closeSettings() {
        settingsOpen = false
    }

    /**
     * A playlist made here, being looked at.
     *
     * Kept beside the card stack rather than inside it: one of these isn't
     * something the catalogue can hand back, so it can't be a card without
     * inventing a kind that means "not really from there".
     */
    var playlist by mutableStateOf<String?>(null)
        private set

    fun openPlaylist(id: String) {
        playlist = id
        settingsOpen = false
        stack.clear()
    }

    fun closePlaylist() {
        playlist = null
    }

    fun go(to: Destination) {
        destination = to
        settingsOpen = false
        playlist = null
        stack.clear()
    }

    /**
     * Open a collection.
     *
     * Songs are not a place — they play where they are, so they never land
     * here. Opening the same thing twice in a row is ignored, which stops a
     * double click from stacking a page on top of itself.
     */
    fun open(card: Catalogue.Card) {
        if (card.kind == Catalogue.Kind.Song) return
        playlist = null
        if (stack.lastOrNull()?.id == card.id) return
        stack.add(card)
    }

    fun back() {
        stack.removeLastOrNull()
    }
}
