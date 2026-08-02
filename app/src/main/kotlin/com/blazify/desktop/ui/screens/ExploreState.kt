package com.blazify.desktop.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Catalogue

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The browse tab, fetched once.
 *
 * New releases and the genre tiles are the same for everybody and change about
 * as often as a magazine cover, so fetching them again every time somebody
 * glances at the search box is work nobody asked for.
 */
object ExploreState {

    var browse by mutableStateOf<Catalogue.Explore?>(null)
        private set

    suspend fun ensureLoaded() {
        if (browse != null) return
        browse = Catalogue.explore().getOrNull()
    }
}
