package com.blazify.desktop.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Signing in with a Google account, the way a television does it.
 *
 * There is no browser inside this window to show a sign-in page in, and
 * embedding a whole browser engine to display one form would add more weight
 * than the rest of the application put together. The device flow exists for
 * exactly this shape of problem: the app asks for a short code, you type it
 * into a real Google page in your own browser, and the account comes back
 * here. It is a genuine sign-in with Google's own consent screen — nothing is
 * typed into this application, and no password ever passes through it.
 *
 * The identity used is the one televisions sign in with, because that is the
 * flow Google offers for devices that can't show a page.
 */
object GoogleSignIn {

    private const val CLIENT_ID =
        "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube"

    private const val CODE_URL = "https://oauth2.googleapis.com/device/code"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

    private val http by lazy { HttpClient(OkHttp) }
    private val json = Json { ignoreUnknownKeys = true }

    /** What to show someone while they go and approve it. */
    data class Request(
        val deviceCode: String,
        val userCode: String,
        val url: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
    )

    /** What comes back once they have. */
    data class Tokens(val access: String, val refresh: String, val expiresInSeconds: Int)

    /** Ask Google for a code to show. */
    suspend fun request(): Result<Request> = withContext(Dispatchers.IO) {
        runCatching {
            val body = http.submitForm(
                CODE_URL,
                parameters {
                    append("client_id", CLIENT_ID)
                    append("scope", SCOPE)
                },
            ).bodyAsText()

            val fields = json.parseToJsonElement(body).jsonObject
            Request(
                deviceCode = fields["device_code"]!!.jsonPrimitive.content,
                userCode = fields["user_code"]!!.jsonPrimitive.content,
                url = fields["verification_url"]?.jsonPrimitive?.contentOrNull
                    ?: "https://www.google.com/device",
                intervalSeconds = fields["interval"]?.jsonPrimitive?.intOrNull ?: 5,
                expiresInSeconds = fields["expires_in"]?.jsonPrimitive?.intOrNull ?: 1800,
            )
        }
    }

    /**
     * Wait for them to approve it.
     *
     * Google answers "not yet" until the code is entered, and asks to be asked
     * less often if we're too eager — both are ordinary parts of the exchange
     * rather than failures, so only a real refusal or running out of time ends
     * the wait.
     */
    suspend fun await(request: Request): Result<Tokens> = withContext(Dispatchers.IO) {
        runCatching {
            var wait = request.intervalSeconds.toLong()
            val deadline = System.currentTimeMillis() + request.expiresInSeconds * 1000L

            while (System.currentTimeMillis() < deadline) {
                delay(wait * 1000)

                val body = http.submitForm(
                    TOKEN_URL,
                    parameters {
                        append("client_id", CLIENT_ID)
                        append("client_secret", CLIENT_SECRET)
                        append("device_code", request.deviceCode)
                        append("grant_type", DEVICE_GRANT)
                    },
                ).bodyAsText()

                val fields = json.parseToJsonElement(body).jsonObject
                val access = fields["access_token"]?.jsonPrimitive?.contentOrNull
                if (access != null) {
                    return@runCatching Tokens(
                        access = access,
                        refresh = fields["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        expiresInSeconds = fields["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600,
                    )
                }

                when (fields["error"]?.jsonPrimitive?.contentOrNull) {
                    "authorization_pending" -> Unit
                    "slow_down" -> wait += 5
                    "access_denied" -> error("You turned it down")
                    "expired_token" -> error("The code ran out — try again")
                    else -> Unit
                }
            }
            error("The code ran out — try again")
        }
    }

    /** Trade a stored refresh token for a working one. */
    suspend fun refresh(token: String): Result<Tokens> = withContext(Dispatchers.IO) {
        runCatching {
            val body = http.submitForm(
                TOKEN_URL,
                parameters {
                    append("client_id", CLIENT_ID)
                    append("client_secret", CLIENT_SECRET)
                    append("refresh_token", token)
                    append("grant_type", "refresh_token")
                },
            ).bodyAsText()

            val fields = json.parseToJsonElement(body).jsonObject
            Tokens(
                access = fields["access_token"]!!.jsonPrimitive.content,
                // A refresh only ever hands back a new access token, so the one
                // we already have stays the way back next time.
                refresh = token,
                expiresInSeconds = fields["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600,
            )
        }
    }
}
