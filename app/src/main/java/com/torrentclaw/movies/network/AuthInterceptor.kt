package com.torrentclaw.movies.network

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

        return chain.proceed(builder.build())
    }
}
