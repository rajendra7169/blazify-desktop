package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What is that.
 *
 * The one question a music player cannot normally answer, because the song is
 * coming out of somebody else's speakers — a café, a car, a television in the
 * next room. Ten seconds through the microphone is enough for a service to
 * recognise, and then it is an ordinary song that can be found and played
 * here like any other.
 *
 * The listening is done by this machine and the recognising is not. Nothing
 * here fingerprints audio: that is a hard, patented, well-solved problem, and
 * the honest thing is to send ten seconds to somebody who has solved it rather
 * than pretend to.
 *
 * The token is the listener's own. One shipped inside an open repository is a
 * token anybody can lift, and a quota that gets used up is a feature that
 * stops working for everybody at once.
 */
object Recognise {

    private val store: File get() = File(Store.folder, "recognise")

    var token by mutableStateOf(runCatching { store.readText().trim() }.getOrDefault(""))
        private set

    val ready: Boolean get() = token.isNotBlank()

    fun chooseToken(value: String) {
        token = value.trim()
        runCatching { store.writeText(token) }
    }

    /** Whether this machine has anything to listen with. */
    val canListen: Boolean
        get() = runCatching {
            AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, FORMAT))
        }.getOrDefault(false)

    /** What came back, when something did. */
    data class Heard(
        val title: String,
        val artist: String,
        val album: String?,
        val artwork: String?,
    )

    sealed interface Answer {
        data class Found(val song: Heard) : Answer
        data object Unknown : Answer
        data class Trouble(val why: String) : Answer
    }

    /**
     * Listen for a few seconds, then ask.
     *
     * Ten, which is what these services want: shorter and a chorus can be all
     * drums, longer and somebody is holding a laptop towards a speaker for an
     * uncomfortable length of time.
     */
    suspend fun listen(seconds: Int = 10): Answer = withContext(Dispatchers.IO) {
        if (!ready) return@withContext Answer.Trouble("No recognition service set up yet")

        val recorded = runCatching { record(seconds) }
            .getOrElse { return@withContext Answer.Trouble("Couldn't use the microphone") }
            ?: return@withContext Answer.Trouble("Couldn't use the microphone")

        val reply = runCatching {
            LyricsProviders.http.post("https://api.audd.io/") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("api_token", token)
                            append("return", "apple_music,spotify")
                            append(
                                "file", recorded,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, "filename=\"heard.wav\"")
                                },
                            )
                        },
                    ),
                )
            }.bodyAsText()
        }.getOrElse { return@withContext Answer.Trouble("Couldn't reach the recognition service") }

        runCatching {
            val body = LyricsProviders.json.parseToJsonElement(reply).jsonObject
            when (body["status"]?.jsonPrimitive?.content) {
                "success" -> {
                    val song = body["result"]?.jsonObject
                        ?: return@runCatching Answer.Unknown
                    Answer.Found(
                        Heard(
                            title = song["title"]?.jsonPrimitive?.content ?: return@runCatching Answer.Unknown,
                            artist = song["artist"]?.jsonPrimitive?.content.orEmpty(),
                            album = song["album"]?.jsonPrimitive?.content,
                            artwork = song["apple_music"]?.jsonObject
                                ?.get("artwork")?.jsonObject
                                ?.get("url")?.jsonPrimitive?.content
                                ?.replace("{w}", "400")?.replace("{h}", "400"),
                        ),
                    )
                }
                // The service says plainly when a token is wrong or spent, and
                // that is worth passing on rather than reporting as silence.
                "error" -> Answer.Trouble(
                    body["error"]?.jsonObject?.get("error_message")?.jsonPrimitive?.content
                        ?: "The recognition service refused",
                )
                else -> Answer.Unknown
            }
        }.getOrDefault(Answer.Unknown)
    }

    /**
     * Ten seconds of whatever this machine can hear, as an ordinary sound file.
     *
     * Mono, sixteen bits, forty-four thousand times a second — what a
     * recogniser wants and no more. Recording in stereo at studio rates would
     * treble the upload for information nothing downstream looks at.
     */
    private fun record(seconds: Int): ByteArray? {
        val info = DataLine.Info(TargetDataLine::class.java, FORMAT)
        if (!AudioSystem.isLineSupported(info)) return null

        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(FORMAT)
        line.start()

        val wanted = FORMAT.frameSize * FORMAT.frameRate.toInt() * seconds
        val heard = java.io.ByteArrayOutputStream(wanted)
        val chunk = ByteArray(4096)
        val until = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < until) {
            val read = line.read(chunk, 0, chunk.size)
            if (read > 0) heard.write(chunk, 0, read)
        }
        line.stop()
        line.close()

        val raw = heard.toByteArray()
        if (raw.isEmpty()) return null

        // Wrapped as a wav rather than sent bare: the header is what tells the
        // other end the rate and the width, and without it ten seconds of
        // audio is ten seconds of unlabelled numbers.
        val wrapped = java.io.ByteArrayOutputStream()
        AudioSystem.write(
            AudioInputStream(ByteArrayInputStream(raw), FORMAT, raw.size.toLong() / FORMAT.frameSize),
            AudioFileFormat.Type.WAVE,
            wrapped,
        )
        return wrapped.toByteArray()
    }

    private val FORMAT = AudioFormat(44100f, 16, 1, true, false)
}
