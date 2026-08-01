package com.blazify.innertube

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Minimal logging façade for this module.
 *
 * Deliberately not a logging framework: the client needs four levels and a tag,
 * and pulling in a configurable backend for that would mean shipping a config
 * file users never asked for. Quiet by default — set [enabled] to see the
 * request traffic while debugging a parser.
 */
object Log {
    @Volatile var enabled: Boolean = System.getenv("BLAZIFY_DEBUG") != null

    private var tag: String = "innertube"

    fun tag(name: String): Log = apply { tag = name }

    fun v(message: String, vararg args: Any?) = write("V", message, args, null)
    fun d(message: String, vararg args: Any?) = write("D", message, args, null)
    fun i(message: String, vararg args: Any?) = write("I", message, args, null)
    fun w(message: String, vararg args: Any?) = write("W", message, args, null)
    fun w(error: Throwable?, message: String = "", vararg args: Any?) = write("W", message, args, error)
    fun e(message: String, vararg args: Any?) = write("E", message, args, null)
    fun e(error: Throwable?, message: String = "", vararg args: Any?) = write("E", message, args, error)

    private fun write(level: String, message: String, args: Array<out Any?>, error: Throwable?) {
        if (!enabled && level != "E") return
        val text = if (args.isEmpty()) message else runCatching { message.format(*args) }.getOrDefault(message)
        val line = "[$level/$tag] $text"
        if (level == "E") System.err.println(line) else println(line)
        error?.printStackTrace()
    }
}
