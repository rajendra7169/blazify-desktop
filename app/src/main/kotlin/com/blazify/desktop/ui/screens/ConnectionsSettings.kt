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
import com.blazify.desktop.data.Presence
import com.blazify.desktop.data.Recognise
import com.blazify.desktop.data.Scrobbler
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Somewhere else that hears about what you play.
 *
 * A listening history that outlives this machine is worth having, and it is
 * also the one thing on this screen that sends anything anywhere — so it is off
 * until it's set up, and it says plainly what leaves and what doesn't.
 */
@Composable
fun ConnectionsSettingsSection(
    section: @Composable (String, (() -> Unit)?, @Composable () -> Unit) -> Unit,
) {
    section("Last.fm", null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (Scrobbler.signedIn) SignedIn() else SignIn()
        }
    }

    section("Recognising a song from the air", null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "The \"What's this\" button on the home page listens through the " +
                    "microphone for ten seconds and asks a service what it heard. The " +
                    "listening happens here; the recognising does not — fingerprinting " +
                    "audio is a hard problem somebody else has solved properly.",
                color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
            )
            // The token is the listener's own, for the same reason the Last.fm
            // key is: one shipped inside an open repository is a token anybody
            // can lift, and a quota spent by strangers is a feature that stops
            // working for everybody at once.
            Entry("Token", "From audd.io", Recognise.token, onValue = Recognise::chooseToken)
            Text(
                if (!Recognise.canListen) {
                    "This machine has no microphone that can be opened, so there is nothing " +
                        "to listen with."
                } else if (Recognise.ready) {
                    "Ready. Press \"What's this\" on the home page while music is playing " +
                        "near you."
                } else {
                    "Get a free token at audd.io — it takes a minute and it stays yours."
                },
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )
        }
    }

    section("Discord", null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Toggle("Show what I'm listening to", Presence.enabled, Presence::choose)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(
                        if (Presence.connected) Blaze.Amber else Blz.dim,
                    ),
                )
                Text(
                    when {
                        !Presence.enabled -> "Off"
                        Presence.connected -> "Talking to Discord on this computer"
                        else -> Presence.trouble ?: "Looking for Discord…"
                    },
                    color = if (Presence.connected) Blaze.Amber else Blz.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            if (Presence.enabled) {
                Toggle("Include the album cover", Presence.showArtwork, Presence::chooseArtwork)
                Toggle("Offer a link to the song", Presence.showLink, Presence::chooseLink)
                Entry(
                    "Application id",
                    "Whose name and icon appear beside the song",
                    Presence.appId,
                    onValue = Presence::chooseAppId,
                )
            }

            Text(
                "This talks to the Discord already running on this computer, over a pipe " +
                    "it opens for exactly this. There is no account to connect and no " +
                    "token to hand over — close Discord and nothing is sent anywhere.",
                color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
            )
        }
    }

    if (Scrobbler.signedIn) {
        section("What gets sent", null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Toggle("Record what I listen to", Scrobbler.scrobbling, Scrobbler::chooseScrobbling)
                Toggle(
                    "Show what's playing right now",
                    Scrobbler.announceNowPlaying,
                    Scrobbler::chooseAnnounce,
                )
                Toggle("Liking a song loves it there too", Scrobbler.mirrorLikes, Scrobbler::chooseMirrorLikes)
                Text(
                    "A song is recorded once it has run past halfway, or four minutes, " +
                        "whichever comes first — so skipping through an album doesn't fill " +
                        "your history with songs you heard eight seconds of.",
                    color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                )
                Text(
                    "The title, artist and length go. Nothing else does: not what else is " +
                        "queued, not where the song came from, not anything about this " +
                        "computer.",
                    color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun SignedIn() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                Scrobbler.username?.take(1)?.uppercase().orEmpty(),
                color = Blaze.OnAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                Scrobbler.username.orEmpty(), color = Blz.ink, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Signed in", color = Blaze.Amber, fontSize = 11.5.sp)
        }
        Pill("Sign out", filled = false) { Scrobbler.signOut() }
    }
}

@Composable
private fun SignIn() {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        "Keeps a record of everything you play, on your own account, where it " +
            "outlives this computer.",
        color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
    )

    // The key is the listener's own, deliberately. One shipped inside an open
    // repository is a key anybody can lift, and a key that gets abused is
    // revoked for everyone using it.
    Entry("API key", "From last.fm/api/account/create", Scrobbler.apiKey, onValue = Scrobbler::chooseApiKey)
    Entry("Shared secret", "Shown beside the key", Scrobbler.secret, onValue = Scrobbler::chooseSecret)
    Text(
        "Make a key at last.fm/api/account/create — it takes a minute, and it stays " +
            "yours. Nothing is baked into this build for you to share with strangers.",
        color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
    )

    Entry("Username", "Your Last.fm name", name) { name = it }
    Entry("Password", "Sent once, never stored", password, secret = true) { password = it }

    Scrobbler.trouble?.let {
        Text(it, color = Blaze.Amber, fontSize = 11.5.sp, lineHeight = 17.sp)
    }

    Pill(if (Scrobbler.busy) "Signing in…" else "Sign in", filled = true) {
        if (!Scrobbler.busy && name.isNotBlank() && password.isNotBlank()) {
            Scrobbler.signIn(name, password)
            password = ""
        }
    }
    Text(
        "The password is sent once and never written down. What's kept is the session " +
            "key the service hands back, which you can revoke from your account page " +
            "without changing your password.",
        color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
    )
}

@Composable
private fun Entry(
    label: String,
    hint: String,
    value: String,
    secret: Boolean = false,
    onValue: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Blz.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Blz.surfaceHigh)
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            if (value.isEmpty()) Text(hint, color = Blz.dim, fontSize = 12.5.sp)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                visualTransformation = if (secret) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                textStyle = TextStyle(color = Blz.ink, fontSize = 12.5.sp),
                cursorBrush = SolidColor(Blaze.Amber),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { Typing.active = it.isFocused },
            )
        }
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (filled) Blaze.OnAmber else Blz.ink,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
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
