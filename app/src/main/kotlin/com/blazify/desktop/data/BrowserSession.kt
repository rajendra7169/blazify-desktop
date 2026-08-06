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
     * One browser this app knows how to look for.
     *
     * A table rather than a chain of if-statements: browsers differ only in
     * where they keep their cookies and which keyring name they lock them
     * with, so adding a new one should be adding a line — not editing logic
     * that already works for a dozen others.
     */
    private data class Known(
        val label: String,
        /** Under the config directory on Linux, under the data directory on Windows. */
        val linux: String,
        val windows: String,
        /** The keyring entry it locks its cookies with; Chromium-derived only. */
        val keyring: String = "chromium",
        /** Its flatpak application id, when it ships as one. */
        val flatpak: String? = null,
        /** Its snap directory, when it ships as one. */
        val snap: String? = null,
    )

    /**
     * Everything with a Chromium cookie store worth looking in.
     *
     * Release channels are listed separately because they are separate
     * installations with separate sessions — somebody who does their browsing
     * in Chrome Beta is signed in there and nowhere else, and only offering
     * them stable Chrome is offering them nothing.
     */
    private val CHROMIUM = listOf(
        Known("Chrome", "google-chrome", "Google\\Chrome", "chrome", "com.google.Chrome"),
        Known("Chrome Beta", "google-chrome-beta", "Google\\Chrome Beta", "chrome"),
        Known("Chrome Dev", "google-chrome-unstable", "Google\\Chrome Dev", "chrome"),
        Known("Chromium", "chromium", "Chromium", "chromium", "org.chromium.Chromium", "chromium"),
        Known("Brave", "BraveSoftware/Brave-Browser", "BraveSoftware\\Brave-Browser", "brave", "com.brave.Browser", "brave"),
        Known("Brave Beta", "BraveSoftware/Brave-Browser-Beta", "BraveSoftware\\Brave-Browser-Beta", "brave"),
        Known("Brave Nightly", "BraveSoftware/Brave-Browser-Nightly", "BraveSoftware\\Brave-Browser-Nightly", "brave"),
        Known("Edge", "microsoft-edge", "Microsoft\\Edge", "chromium", "com.microsoft.Edge"),
        Known("Edge Beta", "microsoft-edge-beta", "Microsoft\\Edge Beta", "chromium"),
        Known("Edge Dev", "microsoft-edge-dev", "Microsoft\\Edge Dev", "chromium"),
        Known("Vivaldi", "vivaldi", "Vivaldi", "vivaldi", "com.vivaldi.Vivaldi"),
        Known("Opera", "opera", "..\\Roaming\\Opera Software\\Opera Stable", "opera", "com.opera.Opera"),
        Known("Opera GX", "opera-gx", "..\\Roaming\\Opera Software\\Opera GX Stable", "opera"),
        Known("Yandex", "yandex-browser", "Yandex\\YandexBrowser", "yandex browser"),
        Known("Thorium", "thorium", "Thorium", "thorium"),
        Known("Arc", "arc", "Packages\\TheBrowserCompany.Arc", "chromium"),
        Known("Whale", "naver-whale", "Naver\\Whale", "chromium"),
    )

    /** Firefox and the browsers built on it, which keep profiles rather than folders. */
    private val GECKO = listOf(
        Known("Firefox", ".mozilla/firefox", "Mozilla\\Firefox\\Profiles", flatpak = "org.mozilla.firefox", snap = "firefox"),
        Known("LibreWolf", ".librewolf", "librewolf\\Profiles", flatpak = "io.gitlab.librewolf-community"),
        Known("Zen", ".zen", "zen\\Profiles", flatpak = "app.zen_browser.zen"),
        Known("Waterfox", ".waterfox", "Waterfox\\Profiles", flatpak = "net.waterfox.waterfox"),
        Known("Floorp", ".floorp", "Floorp\\Profiles", flatpak = "one.ablaze.floorp"),
        Known("Mullvad", ".mullvad-browser", "Mullvad\\Profiles", flatpak = "net.mullvad.MullvadBrowser"),
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

        val found = mutableListOf<Browser>()

        // ── Chromium and everything derived from it ──────────────────────────
        for (known in CHROMIUM) {
            // The same browser is installed three different ways on the same
            // desktop and each way puts it somewhere else entirely, so all of
            // them are looked at and whichever holds a session wins.
            val roots = if (windows) {
                listOf(
                    "$appData\\${known.windows}\\User Data",
                    // Opera and a few others keep the profile at the top and
                    // never grew a User Data level.
                    "$appData\\${known.windows}",
                )
            } else {
                listOfNotNull(
                    "$home/.config/${known.linux}",
                    known.flatpak?.let { "$home/.var/app/$it/config/${known.linux}" },
                    known.snap?.let { "$home/snap/$it/common/.config/${known.linux}" },
                    known.snap?.let { "$home/snap/$it/current/.config/${known.linux}" },
                )
            }

            for (root in roots) {
                // The root itself, because Opera and its kind keep one session
                // and no profile folder; then Default and the numbered ones,
                // because plenty of people keep work in the first and the
                // account they actually listen with in another.
                val profiles = listOf("", "Default") + (1..9).map { "Profile $it" }
                for (profile in profiles) {
                    val at = if (profile.isEmpty()) root else "$root/$profile"
                    // Newer builds moved the store under Network/; older ones
                    // keep it beside the profile. Both are still in the wild.
                    val store = listOf(File("$at/Network/Cookies"), File("$at/Cookies"))
                        .firstOrNull { it.isFile } ?: continue
                    val label = when (profile) {
                        "", "Default" -> known.label
                        else -> "${known.label} · $profile"
                    }
                    found += Browser(label, store, Kind.Chromium, known.keyring)
                }
            }
        }

        // ── Firefox and its descendants ──────────────────────────────────────
        for (known in GECKO) {
            val roots = if (windows) {
                listOf(File("$appData\\..\\Roaming\\${known.windows}"))
            } else {
                listOfNotNull(
                    File("$home/${known.linux}"),
                    known.snap?.let { File("$home/snap/$it/common/${known.linux}") },
                    known.flatpak?.let { File("$home/.var/app/$it/${known.linux}") },
                    known.flatpak?.let { File("$home/.var/app/$it/config/${known.linux.removePrefix(".")}") },
                )
            }

            roots
                .flatMap { it.listFiles().orEmpty().toList() }
                .mapNotNull { profile ->
                    File(profile, "cookies.sqlite").takeIf { it.isFile }?.let { profile.name to it }
                }
                // One profile per window and only the one in use is signed in,
                // so the most recently written is the one worth reading.
                .maxByOrNull { it.second.lastModified() }
                ?.let { (_, store) -> found += Browser(known.label, store, Kind.Firefox) }
        }

        return found
            // The same browser can turn up twice when it is installed twice;
            // the one used most recently is the one somebody is signed in to.
            .groupBy { it.label }
            .map { (_, copies) -> copies.maxBy { it.store.lastModified() } }
            // Most recently used first, so the one they are actually in is
            // tried before the one they installed and forgot.
            .sortedByDescending { it.store.lastModified() }
    }

    /**
     * Whether a browser is still running.
     *
     * Closing the last window is not the same as quitting: most of them stay
     * resident for background pages and a tray icon, and one that is still
     * running is still being handed security cookies it keeps to itself.
     * Somebody who has closed every window and is being told to close the
     * browser has been given an instruction they have already followed — this
     * is how to say the useful thing instead.
     */
    fun isRunning(label: String): Boolean {
        val name = label.substringBefore(" ·").trim().lowercase().replace(" ", "")
        return runCatching {
            val command = if (onWindows) listOf("tasklist") else listOf("pgrep", "-fl", name)
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (onWindows) output.lowercase().contains(name) else output.isNotBlank()
        }.getOrDefault(false)
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
                // Chrome, Edge and Brave from version 127 lock their cookies to
                // the browser itself rather than to the account — the key is
                // held by a service that hands it back only to the program that
                // asked for it. Nothing outside that browser can read them, and
                // no amount of trying here will change that, so it is said
                // plainly along with the two things that do work.
                onWindows && found.cookies.isEmpty() && found.lock == "v20" ->
                    error(
                        "${browser.label} keeps its cookies in a form only it can read. " +
                            "Firefox doesn't, so a sign-in there works — or paste the session " +
                            "by hand, which works with any browser.",
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
        listOf(browser.keyringName, browser.label.substringBefore(" ·").lowercase()).distinct().forEach { name ->
            keyringPassword(name)?.let { passwords += it }
        }

        // Then the command-line tool, for a desktop whose library is elsewhere.
        if (passwords.isEmpty()) {
            listOf(browser.keyringName, browser.label.substringBefore(" ·").lowercase()).distinct().forEach { name ->
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
