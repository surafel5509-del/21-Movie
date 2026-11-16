package com.torrentclaw.movies.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.data.prefs.PreferencesRepository
import com.torrentclaw.movies.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Activity-scoped state: theme + readiness. The splash stays visible until the
 * first DataStore emission arrives, which prevents a dark→light flash for users
 * who chose a light theme.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = preferencesRepository.preferences
        .map { prefs ->
            MainUiState(
                isReady = true,
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                hapticsEnabled = prefs.hapticFeedback,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainUiState(),
        )
}

data class MainUiState(
    val isReady: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val hapticsEnabled: Boolean = true,
)
