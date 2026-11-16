package com.torrentclaw.movies.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.BuildConfig
import com.torrentclaw.movies.data.prefs.PreferencesRepository
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.domain.model.ThemeMode
import com.torrentclaw.movies.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val apiKeyConfigured: Boolean = BuildConfig.TORRENTCLAW_API_KEY.isNotBlank(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = preferencesRepository.preferences
        .map { prefs ->
            SettingsUiState(
                preferences = prefs,
                appVersion = BuildConfig.VERSION_NAME,
                apiKeyConfigured = BuildConfig.TORRENTCLAW_API_KEY.isNotBlank(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

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
