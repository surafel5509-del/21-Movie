package com.torrentclaw.movies.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.BuildConfig
import com.torrentclaw.movies.data.prefs.PreferencesRepository
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.domain.model.ThemeMode
import com.torrentclaw.movies.domain.model.UserPreferences
import com.torrentclaw.movies.network.ApiKeyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val apiKeyConfigured: Boolean = BuildConfig.TORRENTCLAW_API_KEY.isNotBlank(),
    /** Currently effective key (masked in the UI). */
    val apiKey: String = "",
    /** True when the key came from the build, not from the user. */
    val apiKeyFromBuild: Boolean = BuildConfig.TORRENTCLAW_API_KEY.isNotBlank(),
    val apiKeyMessage: String? = null,
) {
    val apiKeyMasked: String
        get() = if (apiKey.length <= 10) "•".repeat(apiKey.length) else "${apiKey.take(8)}…${apiKey.takeLast(4)}"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val apiKeyProvider: ApiKeyProvider,
) : ViewModel() {

    init {
        // Keep the interceptor's key in sync with what is persisted.
        viewModelScope.launch {
            preferencesRepository.apiKey.collect { stored -> apiKeyProvider.applyStoredKey(stored) }
        }
    }

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        preferencesRepository.apiKey,
        apiKeyProvider.apiKey,
        message,
    ) { prefs, storedKey, effectiveKey, msg ->
        SettingsUiState(
            preferences = prefs,
            appVersion = BuildConfig.VERSION_NAME,
            apiKeyConfigured = effectiveKey.isNotBlank(),
            apiKey = effectiveKey,
            apiKeyFromBuild = storedKey.isBlank() && BuildConfig.TORRENTCLAW_API_KEY.isNotBlank(),
            apiKeyMessage = msg,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    /**
     * Saves a key pasted from the TorrentClaw dashboard. Persisting it means the
     * anonymous-tier limitation (no info hashes, no downloads) goes away for good.
     */
    fun saveApiKey(raw: String) {
        viewModelScope.launch {
            val cleaned = raw.trim().trim('"', '\'').trim()
            if (cleaned.isNotEmpty() && cleaned.length < 8) {
                message.value = "That key looks too short. Copy it from torrentclaw.com/register."
                return@launch
            }
            preferencesRepository.setApiKey(cleaned)
            apiKeyProvider.setKey(cleaned)
            message.value = if (cleaned.isBlank()) {
                "API key cleared — back to the anonymous tier."
            } else {
                "API key saved. Magnet links and downloads are now enabled."
            }
        }
    }

    fun consumeApiKeyMessage() {
        message.value = null
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        preferencesRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setDynamicColor(enabled)
    }

    fun setPreferredQuality(quality: QualityFilter) = viewModelScope.launch {
        preferencesRepository.setPreferredQuality(quality)
    }

    fun setPreferredSort(sort: SortOption) = viewModelScope.launch {
        preferencesRepository.setPreferredSort(sort)
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setHapticFeedback(enabled)
    }
}
