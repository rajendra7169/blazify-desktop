package com.blazify.desktop.tools

import com.blazify.innertube.YouTube
import kotlinx.coroutines.runBlocking

/** Blazify Project (C) 2026 · Licensed under GPL-3.0 */
fun main() = runBlocking {
    YouTube.visitorData().getOrNull()?.let { YouTube.visitorData = it }
    println("identity: ${YouTube.visitorData?.take(18)}…")

    YouTube.home().fold(
        onSuccess = { page ->
            println("${page.sections.size} sections")
            page.sections.take(10).forEach { s ->
                val kinds = s.items.groupingBy { it::class.simpleName }.eachCount()
                println("  \"${s.title}\"  ${s.items.size} items  $kinds")
            }
        },
        onFailure = { println("failed: ${it::class.simpleName}: ${it.message}") },
    )
}
