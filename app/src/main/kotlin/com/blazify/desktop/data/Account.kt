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
    private const val REFRESH_FILE = "account-refresh"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: File get() = File(Store.folder, FILE)
    private val refreshStore: File get() = File(Store.folder, REFRESH_FILE)

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

    /** Where we are in a sign-in that's under way, if one is. */
    var pending by mutableStateOf<GoogleSignIn.Request?>(null)
        private set

    private var refreshToken: String? = null

    /**
     * Whether the catalogue actually answered as somebody.
     *
     * Holding a credential and being signed in are different things: a token
     * the catalogue ignores looks identical from here until it's used, and
     * saying "signed in" while the feed stays anonymous is the app telling
     * someone their playlists are on the way when they aren't.
     */
    var verified by mutableStateOf(false)
        private set

    val signedIn: Boolean get() = verified

    /** A credential is present, whether or not it has been accepted. */
    val hasCredential: Boolean get() = !cookie.isNullOrBlank() || refreshToken != null

    /**
     * Start a sign-in and see it through.
     *
     * The code and the address are published as soon as Google hands them over,
     * so they can be shown while the waiting happens — the person has to go and
     * do something with them, and making them wait for a spinner first would be
     * making them wait for nothing.
     */
    fun signInWithGoogle(openBrowser: (String) -> Unit) {
        problem = null
        checking = true
        scope.launch {
            GoogleSignIn.request().fold(
                onSuccess = { request ->
                    pending = request
                    openBrowser(request.url)
                    GoogleSignIn.await(request).fold(
                        onSuccess = { keep(it) },
                        onFailure = { problem = it.message ?: "That didn't go through" },
                    )
                },
                onFailure = { problem = "Couldn't reach Google to start signing in" },
            )
            pending = null
            checking = false
        }
    }

    fun cancelSignIn() {
        pending = null
        checking = false
    }

    private fun keep(tokens: GoogleSignIn.Tokens) {
        refreshToken = tokens.refresh.ifBlank { refreshToken }
        YouTube.accessToken = tokens.access
        runCatching { refreshToken?.let { refreshStore.writeText(it) } }
        refresh()
    }

    /**
     * Put the stored session back on the client.
     *
     * Called once at startup rather than left to happen on first use: the very
     * first fetch decides whether the feed is yours or everybody's, and by then
     * it is too late to sign in.
     */
    fun restore() {
        // A signed-in account is preferred to a pasted session when both are
        // on disk: it's the stronger of the two and the one that can renew.
        val stored = runCatching {
            refreshStore.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
        }.getOrNull()

        if (stored != null) {
            refreshToken = stored
            scope.launch {
                GoogleSignIn.refresh(stored).fold(
                    onSuccess = { YouTube.accessToken = it.access; refresh() },
                    onFailure = { problem = "Signed out by Google — sign in again" },
                )
            }
            return
        }

        runCatching { store.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null } }
            .getOrNull()
            ?.let {
                attach(it)
                refresh()
            }
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

        attach(cleaned)
        runCatching { store.writeText(cleaned) }
        refresh()
    }

    fun signOut() {
        verified = false
        refreshToken = null
        pending = null
        YouTube.accessToken = null
        runCatching { refreshStore.delete() }
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
        if (!hasCredential) return
        checking = true
        problem = null
        scope.launch {
            YouTube.accountInfo().fold(
                onSuccess = {
                    name = it.name
                    email = it.email
                    picture = it.thumbnailUrl
                    verified = true
                },
                onFailure = {
                    // A credential that isn't accepted should say so rather
                    // than leave someone wondering why their playlists never
                    // turn up.
                    verified = false
                    problem = "That sign-in wasn't accepted by the catalogue"
                },
            )
            checking = false
        }
    }

    private fun attach(value: String) {
        cookie = value
        YouTube.cookie = value
    }
}
