package com.blazify.desktop.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.desktop.Typing
import com.blazify.desktop.together.Servers
import com.blazify.desktop.together.Together
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The parts of Blaze Together that aren't a room.
 *
 * Same three questions the phone asks: what you're called, what you'll let
 * through without being asked, and which server the rooms live on. Reachable
 * both from here and from a card on the feature's own page, because that is
 * where you are standing when you want them.
 */
@Composable
fun TogetherSettingsSection(
    section: @Composable (String, (() -> Unit)?, @Composable () -> Unit) -> Unit,
) {
    section("You", null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var name by remember { mutableStateOf(Together.username) }
            Text("The name everybody in the room sees", color = Blz.ink, fontSize = 13.5.sp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Blz.surfaceHigh)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                if (name.isEmpty()) Text("Not set", color = Blz.dim, fontSize = 13.sp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it; Together.chooseUsername(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = Blz.ink, fontSize = 13.sp),
                    cursorBrush = SolidColor(Blaze.Amber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { Typing.active = it.isFocused },
                )
            }
            Text(
                "Kept on this computer only. It has nothing to do with your account — a " +
                    "room is a room full of people, and what you want to be called in one " +
                    "isn't always what your email says.",
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )
        }
    }

    section("Without asking me", null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Toggle(
                "Let anyone in who has the code",
                Together.autoApproveJoins,
                Together::chooseAutoApproveJoins,
            )
            Toggle(
                "Add anything suggested to the queue",
                Together.autoApproveSuggestions,
                Together::chooseAutoApproveSuggestions,
            )
            Text(
                "Both only apply while you're hosting. With them off you're asked each " +
                    "time, which is the right default for a code that can be passed on.",
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )
        }
    }

    section("Server", null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(
                    if (Together.link == Together.Link.On) Blaze.Amber else Blz.dim,
                ))
                Text(
                    Servers.DEFAULT,
                    color = Blz.ink, fontSize = 12.5.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                "The same room server the phone app uses, run by the community rather " +
                    "than by us — which is what lets a laptop and a phone share one room. " +
                    "Nothing but room messages ever goes through it; the music is played " +
                    "by each machine from its own source.",
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable { onChange(!on) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Blz.ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(38.dp)
                .height(21.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                    else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
                ),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (on) Blaze.OnAmber else Blz.muted),
            )
        }
    }
}
