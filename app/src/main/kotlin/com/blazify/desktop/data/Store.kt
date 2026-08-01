package com.blazify.desktop.data

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Where the app keeps what's yours.
 *
 * Each platform has a folder set aside for this and putting things anywhere
 * else is what leaves people with stray directories in their home. Windows
 * hands one over in an environment variable; elsewhere the convention is a
 * dotted path under the home folder, with the environment allowed to override
 * it for anyone who has moved theirs.
 *
 * Everything is plain JSON, one file per thing. It's a few hundred songs at
 * most — a database would be more machinery than the job needs, and a file you
 * can open and read is a file you can fix by hand if it ever goes wrong.
 */
object Store {

    private val json = Json {
        ignoreUnknownKeys = true    // a file written by an older build still loads
        prettyPrint = true
        encodeDefaults = true
    }

    val folder: File by lazy {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
        val home = System.getProperty("user.home")
        val base = when {
            windows -> System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"
            else -> System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        }
        File(base, if (windows) "Blazify" else "blazify").apply { mkdirs() }
    }

    /**
     * Read a list back, or an empty one.
     *
     * A missing file is the ordinary case on a first run, and a corrupt one is
     * survivable: losing a play history is not worth refusing to start over.
     */
    inline fun <reified T> read(name: String): List<T> = try {
        val file = File(folder, name)
        if (file.exists()) format.decodeFromString(file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Write a list out.
     *
     * Written beside the real file and moved into place, so a crash mid-write
     * can't leave half a file behind — either the old one survives intact or
     * the new one lands whole.
     */
    inline fun <reified T> write(name: String, values: List<T>) {
        try {
            val target = File(folder, name)
            val temporary = File(folder, "$name.writing")
            temporary.writeText(format.encodeToString(values))
            temporary.renameTo(target)
        } catch (_: Exception) {
            // Nothing here is worth interrupting playback over.
        }
    }

    /** Exposed so the inline readers and writers above can reach it. */
    @PublishedApi
    internal val format: Json get() = json
}
