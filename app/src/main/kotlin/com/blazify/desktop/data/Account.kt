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
 * songs rather than promotional tiles.
 *
 * There is exactly one credential: the session from a browser you are already
 * signed in to. Google's own sign-in token was tried, and the catalogue
 * ignores it outright — it renews perfectly and buys nothing — so it isn't
 * offered. It is the same session a browser holds; the difference is only
 * inside itself, which is the one thing a window without a browser engine
 * can't do.
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

    /**
     * Whether the catalogue actually answered as somebody.
     *
     * Holding a credential and being signed in are different things, and
     * saying "signed in" while the feed stays anonymous is the app telling
     * someone their playlists are on the way when they aren't.
     */
    var verified by mutableStateOf(false)
        private set

    val signedIn: Boolean get() = verified

    val hasCredential: Boolean get() = !cookie.isNullOrBlank()

    /** Put the stored session back on the client, before anything is fetched. */
    fun restore() {
        // A token from the sign-in that used to be offered would be read as a
        // credential and silently outrank the session, leaving someone signed
        // in to nothing at all. Nothing reads it now, so it goes.
        runCatching { File(Store.folder, "account-refresh").delete() }

        runCatching { store.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null } }
            .getOrNull()
            ?.let {
                attach(it)
                refresh()
            }
    }

    /**
     * Sign in by taking the session out of a browser.
     *
     * Every browser on the machine is tried in turn and the first signed-in
     * one wins — "which browser" is a question the machine can answer for
     * itself, so it shouldn't be asked.
     */
    fun signInFromBrowser() {
        problem = null
        checking = true
        scope.launch {
            val browsers = BrowserSession.installed()
            if (browsers.isEmpty()) {
                problem = "No browser found on this machine"
                checking = false
                return@launch
            }

            val reasons = mutableListOf<String>()
            var refused: String? = null
            for (browser in browsers) {
                val session = BrowserSession.sessionFrom(browser).getOrElse {
                    reasons += "${browser.label}: ${it.message}"
                    null
                } ?: continue

                // Counted by separators, not by '=' — a cookie value is
                // base64 and carries its own padding, which was making
                // eighteen cookies report as twenty-four.
                carried = session.split("; ").count { part -> part.isNotBlank() }
                attach(session)

                // Holding cookies and being signed in are different things, so
                // the catalogue is asked before a browser is taken as the
                // answer. A stale session in the first browser used to end the
                // search and leave the signed-in one further down untried —
                // which matters now that a machine can offer a dozen of them.
                if (adopt()) {
                    runCatching { store.writeText(session) }
                    problem = null
                    checking = false
                    return@launch
                }
                if (refused == null) refused = browser.label
                reasons += "${browser.label}: its session was refused ($carried cookies)"
            }

            // Nothing was accepted, so nothing should be left attached — a
            // refused session in place is an app that looks signed in and
            // answers as nobody.
            cookie = null
            YouTube.cookie = null
            YouTube.useLoginForBrowse = false
            problem = if (refused != null) {
                // The session WAS found and handed over — the catalogue refused
                // it. Saying "not signed in there" is a lie that sends people
                // back to a browser they are already signed into.
                //
                // The usual cause is a browser that is still open: the security
                // cookies rotate every few minutes and the newest values live
                // in memory until it closes, so what is on disk is a session
                // that has already been superseded.
                "$refused's session was refused — close $refused completely and press " +
                    "this again, so it writes its current session to disk"
            } else {
                "No signed-in browser found.\n" + reasons.joinToString("\n")
            }
            checking = false
        }
    }

    /** The same credential, typed rather than fetched. */
    fun signIn(pasted: String) {
        val cleaned = pasted.trim().removePrefix("Cookie:").trim()
        if ("SAPISID" !in cleaned) {
            problem = "That isn't a signed-in session — it has no SAPISID in it"
            return
        }
        attach(cleaned)
        runCatching { store.writeText(cleaned) }
        checking = true
        scope.launch { refreshAndReport(null) }
    }

    fun signOut() {
        verified = false
        cookie = null
        // Signing out is a change of identity too, in the other direction.
        YouTube.visitorData = null
        name = null
        email = null
        picture = null
        problem = null
        YouTube.cookie = null
        YouTube.onCookieRefreshed = null
        YouTube.dataSyncId = null
        YouTube.useLoginForBrowse = false
        runCatching { store.delete() }
    }

    /** Ask the catalogue who this session belongs to. */
    fun refresh() {
        if (!hasCredential) return
        checking = true
        problem = null
        scope.launch { refreshAndReport(null) }
    }

    /** How many cookies the last browser import handed over, for saying so. */
    private var carried: Int? = null

    /**
     * Ask the catalogue who this session belongs to, and keep it if it answers.
     *
     * Says only whether it worked; what to tell someone about it depends on
     * where the session came from, and that's the caller's business.
     */
    private suspend fun adopt(): Boolean =
        YouTube.accountInfo().fold(
            onSuccess = {
                name = it.name
                email = it.email
                picture = it.thumbnailUrl
                verified = true
                // Whoever this is, their likes are the ones to show.
                Library.syncWithAccount()
                true
            },
            onFailure = {
                verified = false
                false
            },
        )

    private suspend fun refreshAndReport(from: String?) {
        if (!adopt()) {
            // Named when it came from a browser: knowing which one was tried is
            // the difference between "sign in there" and a shrug.
            problem = if (from != null) {
                "$from's session was refused${carried?.let { " ($it cookies)" }.orEmpty()} — " +
                    "close $from completely and press this again, so it writes its " +
                    "current session to disk"
            } else {
                "That session wasn't accepted by the catalogue"
            }
        } else {
            problem = null
        }
        checking = false
    }

    private fun attach(value: String) {
        cookie = value
        YouTube.cookie = value
        // The site rotates the security cookies every few minutes and the only
        // place the new values ever appear is the reply that rotates them.
        // Keeping them is the difference between a sign-in that lasts and one
        // that works for an afternoon and is then refused for good — which is
        // indistinguishable, from outside, from its never having worked.
        YouTube.onCookieRefreshed = { fresh ->
            cookie = fresh
            runCatching { store.writeText(fresh) }
        }
        // The visitor id goes with it. One is minted anonymously the first time
        // anything is fetched, and it belongs to whoever was signed in at the
        // time — which, at startup, is nobody. Carrying that anonymous identity
        // into a signed-in session is a contradiction the catalogue notices,
        // and it refuses the pair. Cleared here so the next request mints one
        // that matches the account it is being sent with.
        YouTube.visitorData = null
        // Browsing signed in is what turns the feed from what's popular into
        // what's yours. Without it the session is carried but never used for
        // the one request the whole home screen is built from.
        YouTube.useLoginForBrowse = true
    }
}
