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

    /**
     * The cookies that make up a signed-in session.
     *
     * Every one of them, not a chosen few: the timestamped pair at the end is
     * required by anything recent and leaving it out gets the whole session
     * treated as anonymous — which looks exactly like a wrong password and
     * isn't. Nothing outside this list is read.
     */
    private val WANTED = setOf(
        "SAPISID", "APISID", "HSID", "SSID", "SID",
        "__Secure-1PAPISID", "__Secure-3PAPISID",
        "__Secure-1PSID", "__Secure-3PSID",
        "__Secure-1PSIDCC", "__Secure-3PSIDCC", "SIDCC",
        // Timestamps that recent sign-ins carry, and refuse to work without.
        "__Secure-1PSIDTS", "__Secure-3PSIDTS",
        "LOGIN_INFO", "VISITOR_INFO1_LIVE", "PREF", "YSC", "SOCS", "__Secure-YEC",
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
            // Installed three different ways on the same desktop, and each way
            // puts the same browser somewhere else entirely. A browser found in
            // one place says nothing about the others, so all of them are
            // looked at and whichever holds a session wins.
            listOf(
                Triple("Chrome", "google-chrome/Default/Cookies", "chrome"),
                Triple("Chromium", "chromium/Default/Cookies", "chromium"),
                Triple("Brave", "BraveSoftware/Brave-Browser/Default/Cookies", "brave"),
                Triple("Edge", "microsoft-edge/Default/Cookies", "chromium"),
                Triple("Vivaldi", "vivaldi/Default/Cookies", "vivaldi"),
            ).flatMap { (label, tail, keyring) ->
                listOf(
                    // Installed from the distribution's own packages.
                    Triple(label, "$home/.config/$tail", keyring),
                    // Installed as a flatpak, which keeps its own home.
                    Triple(label, "$home/.var/app/${flatpakId(label)}/config/$tail", keyring),
                    // Installed as a snap, likewise.
                    Triple(label, "$home/snap/${label.lowercase()}/common/$tail", keyring),
                )
            }
        }

        val found = chromium.mapNotNull { (label, path, keyring) ->
            File(path).takeIf { it.isFile }?.let { Browser(label, it, Kind.Chromium, keyring) }
        }
            // The same browser can turn up twice when it's installed twice;
            // the one used most recently is the one someone is signed in to.
            .groupBy { it.label }
            .map { (_, copies) -> copies.maxBy { it.store.lastModified() } }
            .toMutableList()

        // Firefox keeps a folder per profile with no fixed name, in a place
        // that likewise depends on how it was installed.
        val firefoxRoots = if (windows) {
            listOf(File("$appData\\..\\Roaming\\Mozilla\\Firefox\\Profiles"))
        } else {
            listOf(
                File("$home/.mozilla/firefox"),
                File("$home/snap/firefox/common/.mozilla/firefox"),
                File("$home/.var/app/org.mozilla.firefox/.mozilla/firefox"),
                File("$home/.var/app/org.mozilla.firefox/config/mozilla/firefox"),
            )
        }

        firefoxRoots
            .flatMap { root -> root.listFiles().orEmpty().toList() }
            .mapNotNull { File(it, "cookies.sqlite").takeIf { file -> file.isFile } }
            // A profile per window, and only the one being used is signed in.
            .maxByOrNull { it.lastModified() }
            ?.let { found += Browser("Firefox", it, Kind.Firefox) }

        return found
    }

    /** What a browser is called when it's installed as a flatpak. */
    private fun flatpakId(label: String) = when (label) {
        "Chrome" -> "com.google.Chrome"
        "Chromium" -> "org.chromium.Chromium"
        "Brave" -> "com.brave.Browser"
        "Edge" -> "com.microsoft.Edge"
        "Vivaldi" -> "com.vivaldi.Vivaldi"
        else -> label
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

            // Anything written recently is still in the log beside the store
            // rather than in the store itself — including, for a browser that
            // is open right now, the sign-in someone just did. Copying the
            // store alone reads the world as it was some time ago.
            listOf("-wal", "-shm").forEach { suffix ->
                val extra = File(browser.store.parentFile, browser.store.name + suffix)
                if (extra.isFile) {
                    extra.copyTo(File(copy.parentFile, copy.name + suffix), overwrite = true)
                        .deleteOnExit()
                }
            }

            val found = when (browser.kind) {
                Kind.Firefox -> readFirefox(copy)
                Kind.Chromium -> readChromium(copy, browser)
            }
            copy.delete()
            listOf("-wal", "-shm").forEach { File(copy.parentFile, copy.name + it).delete() }

            // Three different things go wrong here and they need different
            // answers, so they get told apart rather than lumped into one
            // unhelpful "not signed in".
            if ("SAPISID" in found.cookies) {
                return@runCatching found.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
            when {
                found.rows == 0 ->
                    error("${browser.label} has no YouTube cookies — sign in to music.youtube.com there")
                found.cookies.isEmpty() && !onWindows && secret == null && !canReadKeyring() ->
                    error(
                        "${browser.label} locks its cookies with this machine's keyring, and " +
                            "nothing here can read it. Paste the session instead.",
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
                "SELECT name, value FROM moz_cookies " +
                    "WHERE host LIKE '%youtube.com' OR host LIKE '%google.com'",
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
        val key = if (onWindows) windowsKey(browser) else linuxKeys(browser)
        var seen = 0
        var lock = "an unknown scheme"
        val out = linkedMapOf<String, String>()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().executeQuery(
                // Google's own domain too: the sign-in itself lives there, and
                // a session made of only the video site's half of it is no
                // session at all.
                "SELECT name, encrypted_value FROM cookies " +
                    "WHERE host_key LIKE '%youtube.com' OR host_key LIKE '%google.com'",
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
                    val opened =
                        if (onWindows) unlockWindows(raw, key.firstOrNull()) else unlockLinux(raw, key)
                    opened?.let { out[name] = it }
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
    private val onWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /**
     * The key a Chromium browser uses on Windows.
     *
     * Kept in the browser's own settings file, wrapped by the operating
     * system's data protection so only this account can unwrap it — which is
     * why it can be read at all: the app is running as that account.
     */
    private fun windowsKey(browser: Browser): List<SecretKeySpec> = runCatching {
        // The settings file sits two folders above the cookie store.
        val state = File(browser.store.parentFile.parentFile.parentFile, "Local State")
        val text = state.readText()
        val marker = "\"encrypted_key\":\""
        val start = text.indexOf(marker) + marker.length
        val encoded = text.substring(start, text.indexOf('"', start))

        val wrapped = java.util.Base64.getDecoder().decode(encoded)
        // The first five bytes say who wrapped it, and aren't part of the key.
        val body = wrapped.copyOfRange(5, wrapped.size)
        val unwrapped = com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(body)
        listOf(SecretKeySpec(unwrapped, "AES"))
    }.getOrDefault(emptyList())

    /**
     * Unwrap one value on Windows.
     *
     * A different cipher from the one used elsewhere: the scheme marker, then
     * a twelve-byte nonce, the value, and a tag on the end that proves it
     * wasn't tampered with.
     */
    private fun unlockWindows(value: ByteArray?, key: SecretKeySpec?): String? {
        if (value == null || key == null || value.size < 32) return null
        return runCatching {
            val nonce = value.copyOfRange(3, 15)
            val body = value.copyOfRange(15, value.size)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, nonce))
            }.doFinal(body).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * The desktop's password store, asked directly.
     *
     * The command-line tool for this is a separate package that plenty of
     * machines don't have, but the library behind it ships with the desktop
     * itself — so it's asked straight, and the tool is only a fallback for the
     * rare setup where the library is missing instead.
     */
    private interface Secret : com.sun.jna.Library {
        /**
         * A description of what's being looked for.
         *
         * Built rather than named, and told not to insist the name matches, so
         * a browser that files its key under a slightly different heading is
         * still found by what the entry actually says about itself.
         */
        fun secret_schema_new(name: String, flags: Int, vararg attributes: Any?): com.sun.jna.Pointer?

        fun secret_password_lookup_sync(
            schema: com.sun.jna.Pointer?,
            cancellable: com.sun.jna.Pointer?,
            error: com.sun.jna.ptr.PointerByReference?,
            vararg attributes: Any?,
        ): String?
    }

    /** Match on the attributes alone, whatever the entry calls its schema. */
    private const val IGNORE_SCHEMA_NAME = 2

    /** A plain string, which is what the attribute we search on holds. */
    private const val STRING_ATTRIBUTE = 0

    private val secret: Secret? by lazy {
        runCatching { com.sun.jna.Native.load("secret-1", Secret::class.java) }.getOrNull()
    }

    /** Ask the store for one browser's key, by the name it filed it under. */
    private fun keyringPassword(application: String): String? = runCatching {
        val library = secret ?: return null
        val schema = library.secret_schema_new(
            "chrome_libsecret_os_crypt_password_v2",
            IGNORE_SCHEMA_NAME,
            "application", STRING_ATTRIBUTE,
            null,
        ) ?: return null
        library.secret_password_lookup_sync(schema, null, null, "application", application, null)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun linuxKeys(browser: Browser): List<SecretKeySpec> {
        // Every plausible source, keyring first: a wrong key produces garbage
        // that fails its own padding check, so trying several costs nothing but
        // a few microseconds and saves guessing which one a browser used.
        val passwords = mutableListOf<String>()

        // Straight to the library first — no extra package needed.
        listOf(browser.keyringName, browser.label.lowercase()).distinct().forEach { name ->
            keyringPassword(name)?.let { passwords += it }
        }

        // Then the command-line tool, for a desktop whose library is elsewhere.
        if (passwords.isEmpty()) {
            listOf(browser.keyringName, browser.label.lowercase()).distinct().forEach { name ->
                runCatching {
                    val process = ProcessBuilder("secret-tool", "lookup", "application", name).start()
                    val stored = process.inputStream.bufferedReader().readText().trim()
                    if (process.waitFor() == 0 && stored.isNotBlank()) passwords += stored
                }
            }
        }
        // The fallback a browser uses when the desktop has no password store.
        passwords += "peanuts"

        // Nothing came back and there's no tool to ask with: worth saying
        // plainly, because it's one package away from working and otherwise
        // looks like the browser is at fault.
        if (passwords.size == 1 && secret == null && !canReadKeyring()) {
            error("this desktop has no password store to ask — paste the session instead")
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

    private fun unlockLinux(value: ByteArray?, keys: List<SecretKeySpec>): String? {
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
            val text = plain.copyOfRange(0, plain.size - padding)

            // Some versions put a hash of the domain in front of the value and
            // some don't, and nothing in the file says which. Taking the value
            // whole is tried first: chopping thirty-two bytes off a value that
            // never had a prefix silently corrupts every cookie, which is far
            // worse than leaving a prefix on and being told the session is bad.
            readable(text)?.let { return it }
            if (text.size > 32) readable(text.copyOfRange(32, text.size))?.let { return it }
        }
        return null
    }

    /** Text if it's text, null if it's the wrong key or the wrong offset. */
    private fun readable(bytes: ByteArray): String? {
        val text = bytes.toString(Charsets.UTF_8)
        return text.takeIf { it.isNotBlank() && it.all { c -> c.code in 0x20..0x7E } }
    }
}
