package com.blazify.desktop.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Asking the browser for the session instead of reading its files.
 *
 * Reading the files was never the point, it was the only way in — and it has
 * stopped being a way at all. Chrome, Edge and Brave from version 127 keep
 * their cookies under a key a Windows service hands back only to the browser
 * that asked for it, so what is on disk is unreadable to anything else. That
 * left signing in on Windows to people willing to copy a header out of the
 * developer tools, which is not a thing to ask of somebody who wants to listen
 * to music.
 *
 * A browser will simply tell you, though, if you open the door it already has
 * for its own tooling. It answers with the cookies decrypted, because it is
 * the one thing that can decrypt them. No key, no scheme, no version to keep
 * up with, and the same on every platform.
 *
 * Two things fall out of it that matter as much. There is nothing to wait for
 * — the cookies do not have to be committed to disk first, which is the
 * half-minute the window used to sit there for. And a browser that changes how
 * it stores things tomorrow changes nothing here.
 *
 * The door is open only for the seconds it takes, only on this machine's own
 * loopback, and only on the profile this application made. Nothing else is
 * asked and nothing else is listening.
 */
object BrowserTalk {

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(OkHttp) { install(WebSockets) }
    }

    /**
     * The session that browser is holding, if it is holding one.
     *
     * Null while it has nothing worth having — which is most of the time,
     * since this is asked once a second while somebody types a password.
     */
    suspend fun session(profile: File): String? = withContext(Dispatchers.IO) {
        val port = portOf(profile) ?: return@withContext null
        val socket = endpointOf(port) ?: return@withContext null

        val cookies = withTimeoutOrNull(6000) {
            runCatching {
                val session = client.webSocketSession(socket)
                session.send(Frame.Text("""{"id":1,"method":"Storage.getCookies"}"""))
                val reply = (session.incoming.receive() as? Frame.Text)?.readText()
                session.close()
                reply
            }.getOrNull()
        } ?: return@withContext null

        runCatching {
            val rows = json.parseToJsonElement(cookies)
                .jsonObject["result"]?.jsonObject
                ?.get("cookies")?.jsonArray
                ?: return@runCatching null

            val held = linkedMapOf<String, String>()
            for (row in rows) {
                val cookie = row.jsonObject
                val domain = cookie["domain"]?.jsonPrimitive?.content.orEmpty()
                // The catalogue's own and the sign-in's own. A browser holds
                // cookies for everywhere it has been, and none of the rest is
                // any of this application's business.
                if (!domain.endsWith("youtube.com") && !domain.endsWith("google.com")) continue
                val name = cookie["name"]?.jsonPrimitive?.content ?: continue
                val value = cookie["value"]?.jsonPrimitive?.content ?: continue
                if (name.isNotBlank() && value.isNotBlank()) held[name] = value
            }

            // Only when it is actually a signed-in session. Anonymous cookies
            // arrive from the first page load and mean nothing.
            held.takeIf { "SAPISID" in it }
                ?.entries?.joinToString("; ") { "${it.key}=${it.value}" }
        }.getOrNull()
    }

    /**
     * Which port that browser opened its door on.
     *
     * Asked for as zero at launch so the machine picks a free one, and the
     * browser writes back the number it was given. Fixed ports are how two
     * copies of something end up fighting over one.
     */
    private fun portOf(profile: File): Int? = runCatching {
        File(profile, "DevToolsActivePort").readLines().firstOrNull()?.trim()?.toIntOrNull()
    }.getOrNull()

    private suspend fun endpointOf(port: Int): String? = runCatching {
        val body = client.get("http://127.0.0.1:$port/json/version").bodyAsText()
        json.parseToJsonElement(body).jsonObject["webSocketDebuggerUrl"]?.jsonPrimitive?.content
    }.getOrNull()
}
