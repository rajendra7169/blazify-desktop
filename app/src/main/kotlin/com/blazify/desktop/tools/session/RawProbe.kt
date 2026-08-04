package com.blazify.desktop.tools.session

import com.blazify.desktop.data.BrowserSession
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What Google actually says when the session is handed over.
 *
 * The client turns every failure into the same empty answer, which is no use
 * when the question is *why* a complete set of cookies is being refused. This
 * asks the same question by hand and reports the reply's shape: the status, the
 * top-level keys, and whether an account is named anywhere in it.
 *
 * Nothing secret is printed. Cookie values, tokens and the reply itself stay
 * out of the terminal; only their names and whether they were there.
 */
fun main() {
    val browsers = BrowserSession.installed()
    println("browsers: ${browsers.joinToString { it.label }}\n")

    for (browser in browsers) {
        val cookies = kotlinx.coroutines.runBlocking { BrowserSession.sessionFrom(browser) }
            .getOrElse {
                println("${browser.label}: ${it.message}\n")
                continue
            }

        val jar = cookies.split("; ").associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }
        println("${browser.label}: ${jar.size} cookies")

        // The shape of each value, never the value. A cookie that has come out
        // of the store wrong is usually the right length plus a fixed lump of
        // binary, or carries characters no cookie ever has — either shows here
        // without a single secret leaving the machine.
        val safe = Regex("^[A-Za-z0-9_\\-./=|:+%]*$")
        jar.forEach { (name, value) ->
            val odd = if (safe.matches(value)) "clean" else "HAS CHARACTERS A COOKIE NEVER HAS"
            println("    $name: ${value.length} chars, $odd")
        }

        // Before any of the music API: does the site itself think these
        // cookies are somebody? The page says so in one word, which is the
        // whole answer and gives nothing away.
        for (site in listOf("https://music.youtube.com/", "https://www.youtube.com/")) {
            val page = (URI(site).toURL().openConnection() as HttpURLConnection).apply {
                setRequestProperty("cookie", cookies)
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                )
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 20000
            }
            val status = runCatching { page.responseCode }.getOrElse { -1 }
            val html = runCatching {
                page.inputStream.bufferedReader().readText()
            }.getOrDefault("")
            val loggedIn = when {
                "\"LOGGED_IN\":true" in html -> "signed in"
                "\"LOGGED_IN\":false" in html -> "SIGNED OUT"
                else -> "doesn't say"
            }
            val renews = page.headerFields.keys.filterNotNull()
                .filter { it.equals("set-cookie", ignoreCase = true) }
                .flatMap { page.headerFields[it].orEmpty() }
                .map { it.substringBefore('=') }
            println("  $site → $status, the page says $loggedIn, renews: ${renews.ifEmpty { listOf("nothing") }.joinToString()}")
            page.disconnect()
        }

        // The hash Google's own web client sends: the clock, the cookie it
        // signs with, and the site it is being sent to.
        for (signWith in listOf("SAPISID", "__Secure-3PAPISID")) {
            val secret = jar[signWith] ?: continue
            val now = System.currentTimeMillis() / 1000
            val origin = "https://music.youtube.com"
            val hash = MessageDigest.getInstance("SHA-1")
                .digest("$now $secret $origin".toByteArray())
                .joinToString("") { "%02x".format(it) }
            val scheme = if (signWith == "SAPISID") "SAPISIDHASH" else "SAPISID1PHASH"

            for (authUser in listOf(null, "0", "1")) {
                val url = "https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false"
                val body = """
                    {"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20241023.01.00",
                    "hl":"en","gl":"US"},"user":{"lockedSafetyMode":false}}}
                """.trimIndent().replace("\n", "")

                val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("cookie", cookies)
                    setRequestProperty("Authorization", "$scheme ${now}_$hash")
                    setRequestProperty("X-Origin", origin)
                    setRequestProperty("Origin", origin)
                    setRequestProperty("Referer", "$origin/")
                    setRequestProperty("X-Goog-Api-Format-Version", "1")
                    setRequestProperty("X-YouTube-Client-Name", "67")
                    setRequestProperty("X-YouTube-Client-Version", "1.20241023.01.00")
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                    )
                    authUser?.let { setRequestProperty("X-Goog-AuthUser", it) }
                    connectTimeout = 20000
                    readTimeout = 20000
                }
                connection.outputStream.use { it.write(body.toByteArray()) }

                val status = runCatching { connection.responseCode }.getOrElse { -1 }
                val reply = runCatching {
                    (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.readText().orEmpty()
                }.getOrDefault("")

                val named = "activeAccountHeaderRenderer" in reply
                val signedOut = "signInHeaderRenderer" in reply || "authenticateUser" in reply
                val label = "$scheme, authuser=${authUser ?: "none"}"
                println(
                    "  $label → $status, ${reply.length} bytes" +
                        when {
                            named -> ", NAMES AN ACCOUNT"
                            signedOut -> ", says signed out"
                            reply.contains("\"error\"") -> ", carries an error: " +
                                Regex("\"message\":\"([^\"]{0,120})\"").find(reply)?.groupValues?.get(1)
                            else -> ", neither — no account header and no sign-in prompt"
                        },
                )
                if (authUser == null) {
                    val renews = connection.headerFields.keys.filterNotNull()
                        .filter { it.equals("set-cookie", ignoreCase = true) }
                        .flatMap { connection.headerFields[it].orEmpty() }
                        .map { it.substringBefore('=') }
                    println("    renews: ${renews.ifEmpty { listOf("nothing") }.joinToString()}")
                }
                // The renderer names in the reply say what kind of menu came
                // back — an account's menu, a sign-in prompt, or something
                // else entirely. Names only; no contents.
                if (authUser == null) {
                    val shapes = Regex("\"(\\w+Renderer)\"").findAll(reply)
                        .map { it.groupValues[1] }.distinct().take(12).toList()
                    println("    shape: ${shapes.joinToString()}")
                }
                connection.disconnect()
            }
        }
        println()
    }
}
