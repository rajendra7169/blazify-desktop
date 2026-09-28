package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Unscrambling the addresses YouTube hands out.
 *
 * The web client is given a signature it has to put through a function in the
 * service's own player script before the address will work, and a second
 * parameter which decides how fast the audio is allowed to arrive. Both are
 * deliberately unreadable: the script is a few megabytes of generated
 * JavaScript, rewritten every few days, and the two functions are renamed each
 * time.
 *
 * So the script is not read here, it is run — in the browser this machine
 * already has ([Headless]), which is the only thing that can honestly be said
 * to understand it. Two lines are appended to it, inside its own closure, that
 * hand the functions out under names of ours; everything else about it is left
 * exactly as it was served.
 *
 * Which two functions those are comes from a small public table, kept in step
 * with the player script by a job that watches for new versions. Nothing from
 * that table is passed through as written: a function name and two numbers are
 * all it is allowed to contain, and anything else is refused, because every
 * value in it ends up being run.
 */
object PlayerScript {

    /**
     * Where the table of function names is read from, in order.
     *
     * The first is our own copy, kept in step by a scheduled job so playing a
     * song doesn't depend on somebody else's repository staying where it is. The
     * second is the same file through a network that serves it where the first
     * is blocked. The third is the table itself, asked when our copy can't be
     * read or doesn't know this player yet — the hours before the job catches
     * up, or if the job ever stops.
     */
    private val TABLES = listOf(
        "https://raw.githubusercontent.com/rajendra7169/blazify/player-configs/player_configs.json",
        "https://cdn.jsdelivr.net/gh/rajendra7169/blazify@player-configs/player_configs.json",
        "https://raw.githubusercontent.com/MetrolistGroup/faraday/master/registry/player_configs.json",
    )

    /** The small file that names which player script the service is serving today. */
    private const val VERSION_SOURCE = "https://www.youtube.com/iframe_api"

    /** Only a call of exactly this shape is ever run. */
    private val CALL = Regex("""^[A-Za-z0-9${'$'}_]{1,8}\(\d+,\d+,INPUT\)$""")

    /** Only a plain name is ever run. */
    private val NAME = Regex("""^[A-Za-z0-9${'$'}_]{1,8}$""")

    private val VERSION = Regex("""/s/player/([0-9a-z]{8})/""")

    /** What one version of the player script needs to be asked. */
    private data class Recipe(val sig: String, val nClass: String, val sts: Int?)

    /** The player version in use, for settings to show. */
    var version by mutableStateOf<String?>(null)
        private set

    /**
     * The number the service wants quoted back when asking for a song.
     *
     * It says which player script the answer should be scrambled for. Asking
     * without it gets an answer scrambled for a script we aren't holding.
     */
    var timestamp by mutableStateOf<Int?>(null)
        private set

    /** Why it isn't working, if it isn't. */
    var trouble by mutableStateOf<String?>(null)
        private set

    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Mutex()
    private val client by lazy { HttpClient(OkHttp) }

    /** The version the loaded functions belong to, so a new one is noticed. */
    private var loaded: String? = null

    /**
     * Have the functions ready in the engine, fetching what's needed once.
     *
     * Cheap after the first time: it asks the page whether the functions are
     * still there, which is a question and an answer over a local socket, and
     * only does the work again when they aren't — a new player version, or a
     * browser that had to be restarted.
     */
    suspend fun ready(): Boolean = gate.withLock {
        if (loaded != null && Headless.evaluate("typeof window._blazeSig", waitMs = 5_000) == "function") {
            return@withLock true
        }
        loaded = null

        if (!Headless.ready()) {
            trouble = Headless.trouble
            return@withLock false
        }

        val hash = withContext(Dispatchers.IO) { hash() }
        if (hash == null) {
            trouble = "Couldn't find out which player YouTube is serving"
            return@withLock false
        }
        version = hash

        val recipe = withContext(Dispatchers.IO) { recipe(hash) }
        if (recipe == null) {
            trouble = "Nothing known yet about player $hash — this usually sorts itself out within hours"
            return@withLock false
        }
        timestamp = recipe.sts

        val script = withContext(Dispatchers.IO) { script(hash) }
        if (script == null) {
            trouble = "Couldn't fetch the player script"
            return@withLock false
        }

        // A document of the service's own origin. The script was written to run
        // on that site and reaches for things about where it is; about:blank is
        // nowhere. This page is the smallest thing served from there that is
        // still a document — the site's own pages would load a second copy of
        // this very script and fight with it.
        if (!Headless.open("https://www.youtube.com/robots.txt")) {
            trouble = "Couldn't open a page to run the player script in"
            return@withLock false
        }

        val ran = Headless.evaluate(wrap(script, recipe), waitMs = 60_000)
        if (ran != "ready") {
            trouble = "The player script wouldn't run"
            return@withLock false
        }

        loaded = hash
        trouble = null
        true
    }

    /**
     * Turn one scrambled offer into an address that works.
     *
     * The offer arrives as a little query string of its own: the signature, the
     * name of the parameter it belongs in, and the address to put it on.
     */
    suspend fun unscramble(offer: String): String? {
        if (!ready()) return null
        val parts = offer.split("&")
            .mapNotNull { piece ->
                val at = piece.indexOf('=')
                if (at <= 0) null else piece.take(at) to decode(piece.substring(at + 1))
            }.toMap()
        val scrambled = parts["s"] ?: return null
        val address = parts["url"] ?: return null
        val into = parts["sp"] ?: "signature"

        val solved = Headless.evaluate("window._blazeSig(${quote(scrambled)})", waitMs = 15_000)
            ?.takeIf { it.isNotBlank() } ?: return null
        val joiner = if ("?" in address) "&" else "?"
        return "$address$joiner$into=${encode(solved)}"
    }

    /**
     * Put the throttle parameter through the same script.
     *
     * Left as it was, the audio is handed over slower than it plays. Handing the
     * address back unchanged when this can't be done is deliberate: a slow song
     * is better than no song, and the caller has no better address to try.
     */
    suspend fun retune(address: String): String {
        if (!ready()) return address
        val at = Regex("""[?&]n=([^&]+)""").find(address) ?: return address
        val was = decode(at.groupValues[1])
        val now = Headless.evaluate("window._blazeN(${quote(was)})", waitMs = 15_000)
            ?.takeIf { it.isNotBlank() && it != was } ?: return address
        return address.replaceRange(at.range, at.value.replace(at.groupValues[1], encode(now)))
    }

    /** Which player script the service is serving right now. */
    private suspend fun hash(): String? = runCatching {
        val body = client.get(VERSION_SOURCE) {
            header("User-Agent", WEB_AGENT)
        }.bodyAsText()
        VERSION.find(body)?.groupValues?.get(1)
    }.getOrNull()

    /** What that version needs, from the first table that knows it. */
    private suspend fun recipe(hash: String): Recipe? {
        for (table in TABLES) {
            val body = runCatching {
                client.get(table) { header("User-Agent", WEB_AGENT) }.bodyAsText()
            }.getOrNull() ?: continue
            val players = runCatching {
                json.parseToJsonElement(body).jsonObject["players"]?.jsonObject
            }.getOrNull() ?: continue

            val entry = players[hash]?.jsonObject ?: players.entries.firstOrNull { (_, value) ->
                runCatching {
                    value.jsonObject["aliases"]?.jsonArray?.any { it.jsonPrimitive.content == hash } == true
                }.getOrDefault(false)
            }?.value?.jsonObject ?: continue

            val recipe = read(entry) ?: continue
            return recipe
        }
        return null
    }

    /**
     * Read one entry, refusing anything that isn't the expected shape.
     *
     * This is the boundary: everything that gets past here is run as JavaScript
     * in a browser, so a name is a name and a number is a number, and a value
     * carrying anything else is not repaired, it is refused.
     */
    private fun read(entry: JsonObject): Recipe? {
        val sig = entry["sig"]?.jsonPrimitive?.content ?: return null
        val nClass = entry["nClass"]?.jsonPrimitive?.content ?: return null
        if (!CALL.matches(sig) || !NAME.matches(nClass)) return null
        val sts = entry["sts"]?.jsonPrimitive?.content?.toIntOrNull()
        return Recipe(sig, nClass, sts)
    }

    /** The player script itself, as served. */
    private suspend fun script(hash: String): String? = runCatching {
        client.get("https://www.youtube.com/s/player/$hash/player_ias.vflset/en_GB/base.js") {
            header("User-Agent", WEB_AGENT)
        }.bodyAsText().takeIf { it.length > 100_000 }
    }.getOrNull()

    /**
     * The script with two lines added, inside its own closure.
     *
     * Both functions live inside the closure the whole script is wrapped in, so
     * nothing outside can reach them — which is the point of writing it that
     * way. Appending to the end of the file would put our lines outside that
     * closure, where the names mean nothing; putting them just before it closes
     * puts them where the names are.
     */
    private fun wrap(script: String, recipe: Recipe): String {
        val closing = "})(_yt_player);"
        val exports = buildString {
            append("; window._blazeSig = function(s){ try { return ")
            append(recipe.sig.replace("INPUT", "s"))
            append(" } catch(e) { return null } };")
            // The throttle is not a function but a small class the script uses
            // to rewrite addresses. Handing it one address with the parameter in
            // it and reading the parameter back out is what the player does.
            append(" window._blazeN = function(n){ try { var u = new g.")
            append(recipe.nClass)
            append("('https://x.googlevideo.com/videoplayback?n=' + n, true);")
            append(" var t = u.get('n'); return (t && t !== n) ? t : n } catch(e) { return n } };")
        }
        val body = if (closing in script) {
            script.replace(closing, "$exports $closing")
        } else {
            // A script shaped differently than expected still gets a chance: the
            // names may be at the top level, in which case this works anyway.
            script + "\n" + exports
        }
        // The value the engine hands back, so "it ran" is something observed
        // rather than assumed.
        return "$body\n(typeof window._blazeSig === 'function' && typeof window._blazeN === 'function') ? 'ready' : 'no'"
    }

    private const val WEB_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    /** A JavaScript string literal, so nothing in the value is read as code. */
    private fun quote(text: String): String = JsonPrimitive(text).toString()
}
