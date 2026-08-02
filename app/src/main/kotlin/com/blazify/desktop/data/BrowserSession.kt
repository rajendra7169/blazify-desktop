package com.blazify.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.DriverManager
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Borrowing the session from a browser that's already signed in.
 *
 * You sign in to Google in your own browser, on Google's own page, and then
 * this brings the result across — no page shown inside this application, no
 * password anywhere near it, and nothing to copy by hand. It reads the cookie
 * store the browser keeps on this machine, takes only what belongs to the
 * catalogue, and leaves everything else alone.
 *
 * Well-trodden ground: it is what command-line downloaders mean by taking
 * cookies from a browser.
 */
object BrowserSession {

    /** How a browser stores what it knows. */
    enum class Kind { Chromium, Firefox }

    data class Browser(val label: String, val store: File, val kind: Kind)

    /** Only the ones that carry the cookies we need. */
    private val WANTED = setOf(
        "SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID",
        "SID", "__Secure-1PSID", "__Secure-3PSID",
        "HSID", "SSID", "APISID", "LOGIN_INFO",
        "VISITOR_INFO1_LIVE", "PREF", "SIDCC", "__Secure-1PSIDCC", "__Secure-3PSIDCC",
    )

    /**
     * Which browsers are on this machine.
     *
     * Only ones with a cookie store on disk are offered — a browser that has
     * never been opened has nothing to give, and listing it would be an
     * invitation to a dead end.
     */
    fun installed(): List<Browser> {
        val home = System.getProperty("user.home")
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
        val appData = System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local"

        val chromium = if (windows) {
            listOf(
                "Chrome" to "$appData\\Google\\Chrome\\User Data\\Default\\Network\\Cookies",
                "Edge" to "$appData\\Microsoft\\Edge\\User Data\\Default\\Network\\Cookies",
                "Brave" to "$appData\\BraveSoftware\\Brave-Browser\\User Data\\Default\\Network\\Cookies",
            )
        } else {
            listOf(
                "Chrome" to "$home/.config/google-chrome/Default/Cookies",
                "Chromium" to "$home/.config/chromium/Default/Cookies",
                "Brave" to "$home/.config/BraveSoftware/Brave-Browser/Default/Cookies",
                "Edge" to "$home/.config/microsoft-edge/Default/Cookies",
                "Vivaldi" to "$home/.config/vivaldi/Default/Cookies",
            )
        }

        val found = chromium.mapNotNull { (label, path) ->
            File(path).takeIf { it.isFile }?.let { Browser(label, it, Kind.Chromium) }
        }.toMutableList()

        // Firefox keeps a folder per profile with no fixed name, so the one
        // that has been used most recently is the one meant.
        val profiles = File(
            if (windows) "$appData\\..\\Roaming\\Mozilla\\Firefox\\Profiles" else "$home/.mozilla/firefox",
        )
        profiles.listFiles()
            ?.mapNotNull { File(it, "cookies.sqlite").takeIf { file -> file.isFile } }
            ?.maxByOrNull { it.lastModified() }
            ?.let { found += Browser("Firefox", it, Kind.Firefox) }

        return found
    }

    /**
     * Take the catalogue's cookies out of a browser.
     *
     * Returns the header the catalogue expects, or null when that browser
     * isn't signed in — which is the ordinary case for a browser someone has
     * simply never used for this.
     */
    suspend fun sessionFrom(browser: Browser): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Copied first: a running browser holds a lock on its own store,
            // and asking someone to quit their browser to sign in here would
            // be a poor trade.
            val copy = File.createTempFile("blazify-cookies", ".db").apply { deleteOnExit() }
            browser.store.copyTo(copy, overwrite = true)

            val cookies = when (browser.kind) {
                Kind.Firefox -> readFirefox(copy)
                Kind.Chromium -> readChromium(copy, browser)
            }
            copy.delete()

            if ("SAPISID" !in cookies) error("${browser.label} isn't signed in to YouTube Music")
            cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
    }

    private fun readFirefox(file: File): Map<String, String> {
        val out = linkedMapOf<String, String>()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().executeQuery(
                "SELECT name, value FROM moz_cookies WHERE host LIKE '%youtube.com'",
            ).use { rows ->
                while (rows.next()) {
                    val name = rows.getString(1)
                    if (name in WANTED) out[name] = rows.getString(2)
                }
            }
        }
        return out
    }

    private fun readChromium(file: File, browser: Browser): Map<String, String> {
        val key = chromiumKey(browser)
        val out = linkedMapOf<String, String>()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().executeQuery(
                "SELECT name, encrypted_value FROM cookies WHERE host_key LIKE '%youtube.com'",
            ).use { rows ->
                while (rows.next()) {
                    val name = rows.getString(1)
                    if (name !in WANTED) continue
                    decrypt(rows.getBytes(2), key)?.let { out[name] = it }
                }
            }
        }
        return out
    }

    /**
     * The key a Chromium browser locks its cookies with.
     *
     * It asks the desktop's own password store for one, and falls back to a
     * fixed word when there isn't one — so both cases have to be tried, and
     * there's no way to know in advance which was used.
     */
    private fun chromiumKey(browser: Browser): List<SecretKeySpec> {
        val passwords = mutableListOf("peanuts")
        // The keyring entry is named after the browser, and reading it needs
        // the desktop's own tool — absent on a machine with no keyring, which
        // is exactly when the fallback applies.
        runCatching {
            val process = ProcessBuilder(
                "secret-tool", "lookup", "application", browser.label.lowercase(),
            ).start()
            val stored = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && stored.isNotBlank()) passwords.add(0, stored)
        }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return passwords.map {
            SecretKeySpec(factory.generateSecret(PBEKeySpec(it.toCharArray(), SALT, 1, 128)).encoded, "AES")
        }
    }

    private val SALT = "saltysalt".toByteArray()
    private val IV = ByteArray(16) { ' '.code.toByte() }

    private fun decrypt(value: ByteArray?, keys: List<SecretKeySpec>): String? {
        if (value == null || value.size < 4) return null
        val body = value.copyOfRange(3, value.size)

        for (key in keys) {
            val plain = runCatching {
                Cipher.getInstance("AES/CBC/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, IvParameterSpec(IV))
                }.doFinal(body)
            }.getOrNull() ?: continue

            // Padding is however many bytes the last one says.
            val padding = plain.lastOrNull()?.toInt() ?: 0
            if (padding !in 1..16 || padding > plain.size) continue
            var text = plain.copyOfRange(0, plain.size - padding)

            // Newer versions put a hash of the domain in front of the value.
            // It isn't text, which is how it can be told apart from one.
            if (text.size > 32 && text.take(32).any { it < 0x20 }) {
                text = text.copyOfRange(32, text.size)
            }

            val decoded = text.toString(Charsets.UTF_8)
            if (decoded.isNotBlank() && decoded.all { it.code in 0x20..0x7E }) return decoded
        }
        return null
    }
}
