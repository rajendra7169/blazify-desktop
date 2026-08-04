package com.blazify.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Everywhere the rail can take you.
 *
 * [section] splits the rail into groups — the browsing destinations at the top,
 * then the ones that are yours. Order here is the order on screen.
 */
enum class Destination(
    val label: String,
    val icon: ImageVector,
    val section: Section = Section.Browse,
) {
    Home("Home", Icons.Rounded.Home),
    Explore("Explore", Icons.Rounded.Search),
    // Its own place in the rail rather than a filter inside search. Listening
    // to a programme and listening to music are different evenings, and one of
    // them was only reachable by typing its name from memory.
    Podcasts("Podcasts", Icons.Rounded.Podcasts),
    Library("Library", Icons.Rounded.LibraryMusic),
    Together("Blaze Together", Icons.Rounded.People),

    Liked("Liked songs", Icons.Rounded.Favorite, Section.Yours),
    Downloads("Downloads", Icons.Rounded.Download, Section.Yours),
    OnThisComputer("On this computer", Icons.Rounded.Computer, Section.Yours),
    TopSongs("Top songs", Icons.Rounded.TrendingUp, Section.Yours),
    History("History", Icons.Rounded.History, Section.Yours);

    enum class Section(val title: String?) { Browse(null), Yours("Yours") }
}
