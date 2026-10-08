package com.blazify.desktop.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.data.Store
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Correcting for a particular pair of headphones.
 *
 * AutoEq is a public collection of measured headphones, each with the curve
 * that brings it towards a neutral target. Nothing of it is shipped here: the
 * list of models is fetched the first time somebody searches, kept for a week,
 * and a model's curve is fetched only when it is picked.
 *
 * The curve comes from the "GraphicEQ" file, which is the correction itself
 * written out at a hundred-odd frequencies. The player has ten fixed bands, so
 * the curve is read at each band's centre — on a log scale, the way hearing
 * works — and the result is an approximation: right in shape, coarser in
 * detail, and never pretending otherwise.
 */
object AutoEq {

    private const val ROOT = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
    private const val INDEX_FILE = "autoeq-index.md"
    private const val WEEK = 7L * 24 * 60 * 60 * 1000

    /** One measured model, as the index lists it. */
    data class Model(val name: String, val source: String, val path: String)

    private val client by lazy { HttpClient(OkHttp) { expectSuccess = false } }

    private var index: List<Model> = emptyList()

    var busy by mutableStateOf(false)
        private set
    var trouble by mutableStateOf<String?>(null)
        private set

    /**
     * Models whose names contain every word typed, best matches first.
     *
     * A name that starts with what was typed beats one that merely contains it,
     * so "hd 6" finds the HD 600 before somebody's HD 6XX review of something else.
     */
    suspend fun search(query: String, limit: Int = 30): List<Model> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        trouble = null
        val all = loadIndex()
        val lowered = query.trim().lowercase()
        return all.asSequence()
            .filter { model -> words.all { model.name.lowercase().contains(it) } }
            .sortedWith(
                compareByDescending<Model> { it.name.lowercase().startsWith(lowered) }
                    .thenBy { it.name.length },
            )
            .take(limit)
            .toList()
    }

    /**
     * The model's correction, as ten band gains and a level, for these bands.
     *
     * Returned rather than applied, so the caller decides what it is called and
     * when it goes on.
     */
    suspend fun correction(model: Model, centres: List<Float>): Result<Pair<List<Float>, Float>> =
        withContext(Dispatchers.IO) { fetchCorrection(model, centres) }

    private suspend fun fetchCorrection(model: Model, centres: List<Float>): Result<Pair<List<Float>, Float>> = runCatching {
        busy = true
        trouble = null
        try {
            val last = URLDecoder.decode(model.path.substringAfterLast('/'), Charsets.UTF_8)
            val file = URLEncoder.encode("$last GraphicEQ.txt", Charsets.UTF_8).replace("+", "%20")
            val response = client.get("$ROOT/${model.path.removePrefix("./")}/$file") {
                header("User-Agent", "Blazify")
            }
            if (!response.status.isSuccess()) error("That model's curve isn't available.")
            val curve = parseGraphicEq(response.bodyAsText())
            if (curve.isEmpty()) error("That model's curve couldn't be read.")
            toBands(curve, centres)
        } finally {
            busy = false
        }
    }.onFailure { trouble = it.message ?: "Couldn't fetch that curve." }

    /** The list of models, from the copy kept here when it's recent enough. */
    private suspend fun loadIndex(): List<Model> = withContext(Dispatchers.IO) { readIndex() }

    private suspend fun readIndex(): List<Model> {
        if (index.isNotEmpty()) return index
        val kept = File(Store.folder, INDEX_FILE)
        val fresh = kept.exists() && System.currentTimeMillis() - kept.lastModified() < WEEK
        val text = if (fresh) {
            kept.readText()
        } else {
            busy = true
            trouble = null
            try {
                val response = runCatching {
                    client.get("$ROOT/INDEX.md") { header("User-Agent", "Blazify") }
                }.getOrNull()
                if (response != null && response.status.isSuccess()) {
                    response.bodyAsText().also { body -> runCatching { kept.writeText(body) } }
                } else if (kept.exists()) {
                    // Old is better than nothing while offline.
                    kept.readText()
                } else {
                    trouble = "Couldn't reach the AutoEq list of headphones."
                    return emptyList()
                }
            } finally {
                busy = false
            }
        }
        index = parseIndex(text)
        return index
    }

    // ── reading the files ───────────────────────────────────────────────────

    private val line = Regex("""^- \[(.+?)]\((\./.+?)\) by (.+)$""")

    /** `- [Name](./source/kind/Name) by source on rig`, one per line. */
    fun parseIndex(text: String): List<Model> =
        text.lineSequence().mapNotNull { row ->
            val found = line.find(row.trim()) ?: return@mapNotNull null
            val (name, path, source) = found.destructured
            Model(name, source, path)
        }.toList()

    /** `GraphicEQ: 20 -0.2; 21 -0.2; …` into frequency and gain pairs, lowest first. */
    fun parseGraphicEq(text: String): List<Pair<Float, Float>> =
        text.substringAfter("GraphicEQ:", "")
            .split(';')
            .mapNotNull { point ->
                val parts = point.trim().split(Regex("\\s+"))
                if (parts.size < 2) return@mapNotNull null
                val hertz = parts[0].toFloatOrNull() ?: return@mapNotNull null
                val gain = parts[1].toFloatOrNull() ?: return@mapNotNull null
                if (hertz <= 0f) null else hertz to gain
            }
            .sortedBy { it.first }

    /**
     * The curve read at each band's centre, and a level that keeps it from clipping.
     *
     * Interpolated between the two nearest points on a log-frequency scale; a
     * centre past either end of the curve takes the end's value. The gains are
     * then moved so their middle sits at zero — the faders stay readable —
     * and the level is lowered by however much the highest band is boosted, so
     * nothing is ever pushed above where it started.
     */
    fun toBands(curve: List<Pair<Float, Float>>, centres: List<Float>): Pair<List<Float>, Float> {
        val raw = centres.map { at(curve, it) }
        val middle = raw.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2f }
        val gains = raw.map { (it - middle).coerceIn(-Equaliser.RANGE, Equaliser.RANGE).tenths() }
        val preamp = (-(gains.maxOrNull() ?: 0f)).coerceAtMost(0f).coerceAtLeast(-Equaliser.RANGE).tenths()
        return gains to preamp
    }

    private fun at(curve: List<Pair<Float, Float>>, hertz: Float): Float {
        if (hertz <= curve.first().first) return curve.first().second
        if (hertz >= curve.last().first) return curve.last().second
        val above = curve.indexOfFirst { it.first >= hertz }
        val (f1, g1) = curve[above - 1]
        val (f2, g2) = curve[above]
        val t = (ln(hertz.toDouble()) - ln(f1.toDouble())) / (ln(f2.toDouble()) - ln(f1.toDouble()))
        return (g1 + (g2 - g1) * t).toFloat()
    }

    private fun Float.tenths(): Float = (this * 10).roundToInt() / 10f
}
