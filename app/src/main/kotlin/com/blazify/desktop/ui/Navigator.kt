package com.blazify.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    var destination by mutableStateOf(Destination.Home)
        private set

    private val stack = mutableStateListOf<Catalogue.Card>()

    /** The collection being looked at, if any. */
    val opened: Catalogue.Card? get() = stack.lastOrNull()

    val canGoBack: Boolean get() = stack.isNotEmpty()

    fun go(to: Destination) {
        destination = to
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
        if (stack.lastOrNull()?.id == card.id) return
        stack.add(card)
    }

    fun back() {
        stack.removeLastOrNull()
    }
}
