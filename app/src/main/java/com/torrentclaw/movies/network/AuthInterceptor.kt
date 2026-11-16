package com.torrentclaw.movies.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
/**
 * Attaches the TorrentClaw credentials + client fingerprint to every request.
 *
 * The API accepts the key either as `X-API-Key: <key>` or as
 * `Authorization: Bearer <key>`; we send both so a key pasted from either
 * TorrentClaw dashboard works without code changes. When no key is configured
 * (`BuildConfig.TORRENTCLAW_API_KEY` is blank) the requests degrade gracefully to
 * the anonymous 30 req/min tier instead of failing with 401.
 */
class AuthInterceptor(
    private val apiKeyProvider: ApiKeyProvider,
    private val userAgent: String,
    /** Receives `X-RateLimit-Tier` so Settings can show the real tier. */
    private val onTierObserved: (String) -> Unit = {},
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            // Tells TorrentClaw which client is calling, for their analytics.
            .header("X-Search-Source", "android")

        // Read per request: the user can add a key in Settings without the
        // OkHttp client being rebuilt.
        val apiKey = apiKeyProvider.current
        if (apiKey.isNotBlank()) {
            builder.header("X-API-Key", apiKey)
            builder.header("Authorization", "Bearer $apiKey")
        }

        val response = chain.proceed(builder.build())

        // TorrentClaw states the caller's tier on every response ("pro", "free",
        // "anonymous"). Capturing it turns "is my key working?" from guesswork into
        // something Settings can display, and it is the same signal that tells us
        // whether magnets will come back.
        response.header(HEADER_TIER)?.takeIf { it.isNotBlank() }?.let { tier ->
            val normalised = tier.trim().lowercase()
            if (apiKeyProvider.reportedTier != normalised) {
                apiKeyProvider.reportedTier = normalised
                runCatching { onTierObserved(normalised) }
            }
        }

        return response
    }

    private companion object {
        const val HEADER_TIER = "X-RateLimit-Tier"
    }
}
