package com.torrentclaw.movies.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the TorrentClaw API key that the OkHttp interceptor attaches.
 *
 * Why not pass the key straight into [AuthInterceptor]? The OkHttp client is a
 * singleton built once at startup, but the user can paste a key at any time from
 * Settings. A tiny observable holder lets the interceptor read the *current*
 * value on every request without rebuilding the client (and losing its
 * connection pool, caches and dispatcher).
 *
 * The build-time key (`BuildConfig.TORRENTCLAW_API_KEY`) is only the initial
 * value; a key saved in Settings overrides it. Blank means the anonymous tier,
 * where TorrentClaw omits info hashes and magnets.
 */
@Singleton
class ApiKeyProvider @Inject constructor(
    @BuildTimeApiKey private val buildTimeKey: String,
) {

    private val _apiKey = MutableStateFlow(normalize(buildTimeKey))

    /** Current key; empty string means anonymous. */
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    val current: String
        get() = _apiKey.value

    val isConfigured: Boolean
        get() = _apiKey.value.isNotBlank()

    /**
     * Applies a key from persisted settings. A blank stored value keeps the
     * build-time key so a CI build stays configured even if the user clears the
     * field.
     */
    fun applyStoredKey(stored: String?) {
        val cleaned = normalize(stored)
        _apiKey.value = cleaned.ifBlank { normalize(buildTimeKey) }
    }

    /** Saves a user-entered key (blank = fall back to the build-time key). */
    fun setKey(value: String?) {
        _apiKey.value = normalize(value).ifBlank { normalize(buildTimeKey) }
    }

    /** Parses a key pasted from the dashboard, tolerating quotes and whitespace. */
    private fun normalize(raw: String?): String {
        val cleaned = raw.orEmpty().trim().trim('"', '\'').trim()
        return if (cleaned.length < 8) "" else cleaned
    }
}

/** True when a key is configured, false on the anonymous tier. */
val ApiKeyProvider.isAnonymous: Boolean
    get() = !isConfigured

/**
 * Qualifies the key baked in at build time, so it can be injected as a [String]
 * without clashing with any other string binding.
 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BuildTimeApiKey

