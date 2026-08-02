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

    data class Browser(
        val label: String,
        val store: File,
        val kind: Kind,
        /**
         * What this browser calls itself in the desktop's password store.
         *
         * Each one files its key under its own name, and they don't all match
         * the name people know it by.
         */
        val keyringName: String = "",
    )

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
                Triple("Chrome", "$appData\\Google\\Chrome\\User Data\\Default\\Network\\Cookies", "chrome"),
                Triple("Edge", "$appData\\Microsoft\\Edge\\User Data\\Default\\Network\\Cookies", "chromium"),
                Triple("Brave", "$appData\\BraveSoftware\\Brave-Browser\\User Data\\Default\\Network\\Cookies", "brave"),
            )
        } else {
            listOf(
                Triple("Chrome", "$home/.config/google-chrome/Default/Cookies", "chrome"),
                Triple("Chromium", "$home/.config/chromium/Default/Cookies", "chromium"),
                Triple("Brave", "$home/.config/BraveSoftware/Brave-Browser/Default/Cookies", "brave"),
                Triple("Edge", "$home/.config/microsoft-edge/Default/Cookies", "chromium"),
                Triple("Vivaldi", "$home/.config/vivaldi/Default/Cookies", "vivaldi"),
            )
        }

        val found = chromium.mapNotNull { (label, path, keyring) ->
            File(path).takeIf { it.isFile }?.let { Browser(label, it, Kind.Chromium, keyring) }
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

            val found = when (browser.kind) {
                Kind.Firefox -> readFirefox(copy)
                Kind.Chromium -> readChromium(copy, browser)
            }
            copy.delete()

            // Three different things go wrong here and they need different
            // answers, so they get told apart rather than lumped into one
            // unhelpful "not signed in".
            if ("SAPISID" in found.cookies) {
                return@runCatching found.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
            when {
                found.rows == 0 ->
                    error("${browser.label} has no YouTube cookies — sign in to music.youtube.com there")
                found.cookies.isEmpty() && !canReadKeyring() ->
                    error(
                        "${browser.label} locks its cookies with this machine's keyring, and the " +
                            "reader for it isn't installed. One command fixes it:\n" +
                            "sudo apt install libsecret-tools",
                    )
                found.cookies.isEmpty() ->
                    error(
                        "${browser.label} has ${found.rows} YouTube cookies locked with " +
                            "${found.lock}, and the keyring didn't give up the key",
                    )
                else ->
                    error("${browser.label} is signed out — it has cookies but no SAPISID")
            }
        }
    }

    /** What a store turned out to hold, told apart from what could be read. */
    private data class Found(val rows: Int, val cookies: Map<String, String>, val lock: String = "")

    private fun readFirefox(file: File): Found {
        var rows = 0
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
        return Found(out.size, out)
    }

    private fun readChromium(file: File, browser: Browser): Found {
        val key = chromiumKey(browser)
        var seen = 0
        var lock = "an unknown scheme"
        val out = linkedMapOf<String, String>()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().executeQuery(
                "SELECT name, encrypted_value FROM cookies WHERE host_key LIKE '%youtube.com'",
            ).use { rows ->
                while (rows.next()) {
                    val name = rows.getString(1)
                    if (name !in WANTED) continue
                    seen += 1
                    val raw = rows.getBytes(2)
                    // The first three bytes name the scheme it was locked with,
                    // which is the one fact worth reporting when it won't open.
                    if (raw != null && raw.size > 3) {
                        lock = String(raw, 0, 3, Charsets.US_ASCII)
                    }
                    decrypt(raw, key)?.let { out[name] = it }
                }
            }
        }
        return Found(seen, out, lock)
    }

    /**
     * The key a Chromium browser locks its cookies with.
     *
     * It asks the desktop's own password store for one, and falls back to a
     * fixed word when there isn't one — so both cases have to be tried, and
     * there's no way to know in advance which was used.
     */
    private fun chromiumKey(browser: Browser): List<SecretKeySpec> {
        // Every plausible source, keyring first: a wrong key produces garbage
        // that fails its own padding check, so trying several costs nothing but
        // a few microseconds and saves guessing which one a browser used.
        val passwords = mutableListOf<String>()
        listOf(
            listOf("lookup", "application", browser.keyringName),
            listOf("lookup", "application", browser.label.lowercase()),
            listOf("lookup", "xdg:schema", "chrome_libsecret_os_crypt_password_v2"),
            listOf("lookup", "xdg:schema", "chrome_libsecret_os_crypt_password_v1"),
        ).forEach { arguments ->
            runCatching {
                val process = ProcessBuilder(listOf("secret-tool") + arguments).start()
                val stored = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0 && stored.isNotBlank()) passwords += stored
            }
        }
        // The fallback a browser uses when the desktop has no password store.
        passwords += "peanuts"

        // Nothing came back and there's no tool to ask with: worth saying
        // plainly, because it's one package away from working and otherwise
        // looks like the browser is at fault.
        if (passwords.size == 1 && !canReadKeyring()) {
            error("the keyring reader isn't installed — sudo apt install libsecret-tools")
        }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return passwords.map {
            SecretKeySpec(factory.generateSecret(PBEKeySpec(it.toCharArray(), SALT, 1, 128)).encoded, "AES")
        }
    }

    /** Whether the desktop's password store can be queried at all. */
    private fun canReadKeyring(): Boolean = runCatching {
        ProcessBuilder("secret-tool", "--version").start().waitFor() == 0
    }.getOrDefault(false)

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
