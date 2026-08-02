package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The words, in a language you read.
 *
 * Romanising a song tells you how to sing it; this tells you what you are
 * singing. Different questions, both worth answering, which is why they are two
 * settings rather than one.
 *
 * A whole sheet goes in one request and comes back as one reply — a line at a
 * time would be a hundred requests for one song, and a translator that cannot
 * see the line before has no idea whether "it" is a person or a place.
 */
object Translate {

    /** Separates an index from its line, in and out. */
    private const val MARK = '\u0001'

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 60_000 }
            expectSuccess = false
        }
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val store = File(Store.folder, "translate")

    /** Somewhere that will translate, and where to ask it. */
    enum class Service(val label: String, val endpoint: String, val suggested: String) {
        OpenRouter(
            "OpenRouter",
            "https://openrouter.ai/api/v1/chat/completions",
            "google/gemini-2.5-flash-lite",
        ),
        Gemini(
            "Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-2.5-flash",
        ),
        OpenAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
        Mistral("Mistral", "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest"),
        Custom("Custom", "", ""),
    }

    var on by mutableStateOf(false)
        private set
    var service by mutableStateOf(Service.OpenRouter)
        private set
    var apiKey by mutableStateOf("")
        private set
    var model by mutableStateOf(Service.OpenRouter.suggested)
        private set
    var endpoint by mutableStateOf("")
        private set

    /** What to translate into. A name rather than a code, since that is what is asked for. */
    var language by mutableStateOf(defaultLanguage())
        private set

    var working by mutableStateOf(false)
        private set
    var trouble by mutableStateOf<String?>(null)
        private set

    private val done = mutableStateMapOf<String, Map<Int, String>>()
    private val asked = mutableSetOf<String>()

    val configured: Boolean
        get() = apiKey.isNotBlank() && model.isNotBlank() &&
            (service != Service.Custom || endpoint.isNotBlank())

    init {
        runCatching {
            if (!store.exists()) return@runCatching
            val lines = store.readLines()
            on = lines.getOrNull(0) == "true"
            Service.entries.firstOrNull { it.name == lines.getOrNull(1) }?.let { service = it }
            apiKey = lines.getOrNull(2).orEmpty()
            model = lines.getOrNull(3).orEmpty().ifBlank { service.suggested }
            endpoint = lines.getOrNull(4).orEmpty()
            language = lines.getOrNull(5).orEmpty().ifBlank { defaultLanguage() }
        }
    }

    private fun save() {
        runCatching {
            store.writeText(
                listOf(on.toString(), service.name, apiKey, model, endpoint, language)
                    .joinToString("\n"),
            )
        }
    }

    fun choose(value: Boolean) { on = value; save() }

    fun chooseService(value: Service) {
        service = value
        // The suggested model follows the service, since a model name from one
        // means nothing to another — kept if it was typed by hand.
        if (model.isBlank() || Service.entries.any { it.suggested == model }) {
            model = value.suggested
        }
        save()
    }

    fun chooseApiKey(value: String) { apiKey = value.trim(); save() }
    fun chooseModel(value: String) { model = value.trim(); save() }
    fun chooseEndpoint(value: String) { endpoint = value.trim(); save() }

    fun chooseLanguage(value: String) {
        language = value.trim().ifBlank { defaultLanguage() }
        // A different language is a different translation.
        done.clear()
        asked.clear()
        save()
    }

    private fun defaultLanguage(): String =
        runCatching { java.util.Locale.getDefault().displayLanguage }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "English"

    /** The translated line, if there is one yet. */
    fun lineFor(trackId: String, at: Int): String? = done[trackId]?.get(at)

    /**
     * Translate a sheet, once.
     *
     * Held per song for the session: the same words in the same language give
     * the same answer, and paying twice for it is somebody's money.
     */
    fun prepare(trackId: String, lines: List<String>) {
        if (!on || !configured || lines.isEmpty()) return
        if (trackId in done || trackId in asked) return
        asked += trackId

        working = true
        trouble = null
        scope.launch {
            val answer = ask(lines)
            working = false
            answer.fold(
                onSuccess = { translated ->
                    done[trackId] = translated.withIndex()
                        .filter { (at, text) -> text.isNotBlank() && text != lines.getOrNull(at) }
                        .associate { (at, text) -> at to text }
                },
                onFailure = {
                    asked -= trackId
                    trouble = it.message ?: "The translation service didn't answer."
                },
            )
        }
    }

    private suspend fun ask(lines: List<String>): Result<List<String>> = runCatching {
        val where = if (service == Service.Custom) endpoint else service.endpoint

        // Numbered going in and matched coming out, so a service that merges or
        // drops a line cannot silently shift every line after it onto the wrong
        // timestamp.
        val numbered = lines.mapIndexed { at, text -> "$at$MARK$text" }.joinToString("\n")

        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put(
                                "content",
                                "You translate song lyrics into $language. Every input line is " +
                                    "an index, then character U+0001, then the line. Reply with " +
                                    "one line per input in the same shape: the same index, " +
                                    "U+0001, then only the translation. Keep every line, blank " +
                                    "ones included. No commentary, no notes, no quotation " +
                                    "marks. Translate the meaning, not word by word.",
                            )
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", numbered)
                        },
                    )
                },
            )
        }

        val text = client.post(where) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            // OpenRouter asks callers to say who they are.
            header("HTTP-Referer", "https://github.com/rajendra7169/blazify-desktop")
            header("X-Title", "Blazify")
            setBody(body.toString())
        }.bodyAsText()

        val root = json.parseToJsonElement(text) as? JsonObject
            ?: error("That did not look like a reply.")

        (root["error"] as? JsonObject)?.let { problem ->
            error(problem["message"]?.plain() ?: "The service refused that request.")
        }

        val reply = (root["choices"] as? JsonArray)
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.plain()
            ?: error("The service replied with nothing.")

        val byIndex = reply.lineSequence().mapNotNull { line ->
            val at = line.indexOf(MARK)
            if (at <= 0) return@mapNotNull null
            val number = line.substring(0, at).trim().toIntOrNull() ?: return@mapNotNull null
            number to line.substring(at + 1).trim()
        }.toMap()

        lines.indices.map { byIndex[it].orEmpty() }
    }

    private fun kotlinx.serialization.json.JsonElement.plain(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
