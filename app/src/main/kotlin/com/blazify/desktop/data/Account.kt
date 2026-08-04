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
                expired = false
                attach(session)
                // Asked before anything else: this is the one request that
                // reports an ended session as an ended session, rather than as
                // an answer with nobody in it.
                YouTube.touchSession()

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
                // Three different things end up here and they need three
                // different answers: the site has ended the session, or the
                // browser is still running and keeping the current one to
                // itself, or neither and it is simply old.
                when {
                    expired ->
                        "Google has ended this session. Open music.youtube.com in $refused, " +
                            "sign in again, quit $refused completely, then press this."
                    BrowserSession.isRunning(refused) ->
                        "$refused's session was refused, and $refused is still running — " +
                            "closing its windows isn't enough if it stays in the background " +
                            "or the tray. Quit it properly, then press this."
                    else ->
                        "$refused's session was refused. Open music.youtube.com in $refused " +
                            "to check you're still signed in there, quit it, then press this."
                }
            } else {
                "No signed-in browser found.\n" + reasons.joinToString("\n")
            }
            checking = false
        }
    }

    /**
     * Whether a sign-in window this app opened is still standing open.
     *
     * Worth saying on screen: the app looks like it is doing nothing while it
     * waits, and what it is waiting for is somebody in another window.
     */
    var waitingForWindow by mutableStateOf<String?>(null)
        private set

    /** How far the window has got, for saying so while it gets there. */
    var windowStage by mutableStateOf<SignInWindow.Stage?>(null)
        private set

    /** Whether this machine has a browser a window could be opened in. */
    val canOpenWindow: Boolean get() = SignInWindow.opener() != null

    /**
     * Sign in through a window this app opens, on a profile only it uses.
     *
     * The everyday browser was the wrong thing to borrow from — the site keeps
     * moving the session on and the copy on disk is always the superseded one.
     * A profile nothing else opens holds still, so what is written when the
     * window closes is what is read a moment later.
     */
    fun signInWithWindow() {
        problem = null
        checking = true
        expired = false
        val opener = SignInWindow.opener()
        waitingForWindow = opener?.label ?: "your browser"
        windowStage = null
        scope.launch {
            // The window is watched while it is open: as soon as what it has
            // written down is a session the catalogue accepts, it is closed and
            // that is the end of it. Nobody has to finish a job the app can see
            // is already done.
            SignInWindow.signIn(
                verify = { session -> take(session) },
                onStage = { stage ->
                    // Somebody watching a window that has plainly finished sit
                    // there deserves to know what is being waited for, and that
                    // it is not them.
                    windowStage = stage
                },
            ).fold(
                onSuccess = { session ->
                    waitingForWindow = null
                    windowStage = null
                    // Either it was already taken while the window stood open,
                    // or the window was closed by hand and this is the first
                    // look at what it left behind.
                    if (verified || take(session)) {
                        runCatching { store.writeText(session) }
                        problem = null
                    } else {
                        cookie = null
                        YouTube.cookie = null
                        YouTube.useLoginForBrowse = false
                        problem = if (expired) {
                            "Google ended that session as soon as it was made. Try again, " +
                                "and finish signing in before closing the window."
                        } else {
                            "That window's session wasn't accepted. Try again, and make sure " +
                                "music.youtube.com shows you signed in before you close it."
                        }
                    }
                },
                onFailure = {
                    waitingForWindow = null
                    windowStage = null
                    problem = it.message ?: "The sign-in window couldn't be opened"
                },
            )
            checking = false
        }
    }

    /**
     * Put a session on the client and ask whether it is anybody.
     *
     * The touch first: it is the one request that reports an ended session as
     * ended rather than as an answer with nobody in it.
     */
    private suspend fun take(session: String): Boolean {
        carried = session.split("; ").count { it.isNotBlank() }
        attach(session)
        YouTube.touchSession()
        return adopt()
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
        // The window's profile holds a signed-in session of its own, which is
        // the account itself. Leaving it behind would make signing out a
        // gesture rather than something that happened.
        SignInWindow.forget()
    }

    /**
     * Keep the session current for as long as the app is open.
     *
     * A browser that is left open stays signed in for months, and this is the
     * whole of how: it asks for a page every so often and is handed newer
     * security cookies, which it writes down. Without it a session ages out in
     * an afternoon and is then refused for good — the failure everybody reads
     * as signing in having never worked at all.
     */
    private fun keepAlive() {
        if (alive) return
        alive = true
        scope.launch {
            while (true) {
                if (hasCredential) YouTube.touchSession()
                kotlinx.coroutines.delay(10 * 60 * 1000L)
            }
        }
    }

    private var alive = false

    /** Ask the catalogue who this session belongs to. */
    fun refresh() {
        if (!hasCredential) return
        checking = true
        problem = null
        scope.launch { refreshAndReport(null) }
    }

    /** How many cookies the last browser import handed over, for saying so. */
    private var carried: Int? = null

    /** Whether the site has said, in as many words, that this session is over. */
    private var expired = false

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
                // From here on the session renews itself rather than ageing.
                keepAlive()
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
            expired = false
            cookie = fresh
            runCatching { store.writeText(fresh) }
        }
        // The site says so in as many words when it ends a session, and that
        // is worth repeating rather than translating into a shrug.
        YouTube.onSessionExpired = { expired = true }
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
