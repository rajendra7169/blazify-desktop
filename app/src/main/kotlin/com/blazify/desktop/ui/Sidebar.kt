package com.blazify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The navigation rail down the left.
 *
 * Collapsing hides the labels and narrows to icons — useful on a small laptop
 * screen, and the one piece of chrome that earns an animation, because the
 * content beside it has to move with it.
 */
@Composable
fun Sidebar(
    current: Destination,
    collapsed: Boolean,
    onSelect: (Destination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (collapsed) 62.dp else 212.dp, tween(180), label = "railWidth")

    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(Blz.rail)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Brand(collapsed)
        Spacer(Modifier.size(14.dp))

        var lastSection: Destination.Section? = null
        Destination.entries.forEach { destination ->
            if (destination.section != lastSection) {
                lastSection = destination.section
                destination.section.title?.let { title ->
                    if (!collapsed) SectionLabel(title) else Spacer(Modifier.size(12.dp))
                }
            }
            RailItem(
                destination = destination,
                selected = destination == current,
                collapsed = collapsed,
                onClick = { onSelect(destination) },
            )
        }

        Spacer(Modifier.weight(1f))
        RailFooter(collapsed, onOpenSettings)
    }
}

@Composable
private fun Brand(collapsed: Boolean) {
    Row(
        Modifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        BlazifyLogo(size = 26.dp)
        if (!collapsed) {
            Text("Blazify", color = Blz.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title.uppercase(),
        color = Blz.dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun RailItem(
    destination: Destination,
    selected: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHovered()
    // Amber marks what's active, and nothing else in the rail is amber. Hovering
    // an inactive row lifts its text to full strength — the row answers before
    // you click it, which is what a pointer expects.
    val tint by animateColorAsState(
        when {
            selected -> Blaze.Amber
            hovered.value -> Blz.ink
            else -> Blz.muted
        },
        tween(140), label = "railTint",
    )
    val fill = if (selected) Blaze.Amber.copy(alpha = 0.13f) else Color.Transparent

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .then(if (selected) Modifier else Modifier.hoverBackground(Blz.hover, hovered, source))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(destination.icon, destination.label, Modifier.size(17.dp), tint = tint)
        if (!collapsed) {
            Text(
                destination.label,
                color = tint,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun RailFooter(collapsed: Boolean, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(if (collapsed) "⚙" else "Settings · Equaliser", color = Blz.dim, fontSize = 11.sp)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { ThemeState.cycle() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            if (collapsed) "◐" else "Appearance · ${ThemeState.mode.name}",
            color = Blz.dim,
            fontSize = 11.sp,
        )
    }
}
