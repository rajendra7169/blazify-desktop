package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Being signed in.
 *
 * Signed out the catalogue answers with what's popular; signed in it answers
 * with what's yours — your playlists, your history, the shelves built out of
 * songs rather than promotional tiles. Everything below exists to carry that
 * one difference into the app.
 *
 * The credential is the browser's own session, pasted in. There is no embedded
 * browser to sign in through, and no reasonable way to add one — but a session
 * you already have is a session you can copy, and it costs nothing to keep.
 * It is stored on this machine only and never sent anywhere but the catalogue.
 */
object Account {

    private const val FILE = "account"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: File get() = File(Store.folder, FILE)

    var cookie by mutableStateOf<String?>(null)
        private set

    var name by mutableStateOf<String?>(null)
        private set

    var email by mutableStateOf<String?>(null)
        private set

    var picture by mutableStateOf<String?>(null)
        private set

    var checking by mutableStateOf(false)
        private set

    var problem by mutableStateOf<String?>(null)
        private set

    val signedIn: Boolean get() = !cookie.isNullOrBlank()

    init {
        // Read straight back in, so a restart doesn't sign you out.
        runCatching { if (store.exists()) store.readText().trim().ifBlank { null } }
            .getOrNull()
            ?.let { apply(it) }
    }

    /**
     * Sign in with a session copied from a browser.
     *
     * The whole `Cookie:` header is expected — the parts that matter are
     * `SAPISID` and friends, and picking them out by hand is exactly the sort
     * of instruction people get wrong, so the whole line is taken and the
     * client keeps what it needs.
     */
    fun signIn(pasted: String) {
        val cleaned = pasted.trim().removePrefix("Cookie:").trim()
        if (cleaned.isBlank()) {
            problem = "That looked empty"
            return
        }
        if ("SAPISID" !in cleaned) {
            problem = "That isn't a signed-in session — it has no SAPISID in it"
            return
        }

        apply(cleaned)
        runCatching { store.writeText(cleaned) }
        refresh()
    }

    fun signOut() {
        cookie = null
        name = null
        email = null
        picture = null
        problem = null
        YouTube.cookie = null
        YouTube.dataSyncId = null
        runCatching { store.delete() }
    }

    /** Ask the catalogue who this session belongs to. */
    fun refresh() {
        if (!signedIn) return
        checking = true
        problem = null
        scope.launch {
            YouTube.accountInfo().fold(
                onSuccess = {
                    name = it.name
                    email = it.email
                    picture = it.thumbnailUrl
                },
                onFailure = {
                    // A session that no longer works should say so rather than
                    // leave someone wondering why their playlists are missing.
                    problem = "That session isn't being accepted — sign in again"
                },
            )
            checking = false
        }
    }

    private fun apply(value: String) {
        cookie = value
        YouTube.cookie = value
    }
}
