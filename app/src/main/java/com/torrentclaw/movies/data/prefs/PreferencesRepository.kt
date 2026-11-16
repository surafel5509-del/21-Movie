package com.torrentclaw.movies.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.domain.model.ThemeMode
import com.torrentclaw.movies.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for user settings, backed by Preferences DataStore.
 * Corrupt or unreadable files fall back to [UserPreferences]' defaults instead
 * of crashing at startup.
 */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            UserPreferences(
                themeMode = ThemeMode.fromStorage(prefs[Keys.THEME_MODE]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                preferredQuality = QualityFilter.fromStorage(prefs[Keys.PREFERRED_QUALITY]),
                preferredSort = SortOption.fromStorage(prefs[Keys.PREFERRED_SORT]),
                hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME_MODE, mode.name)

    suspend fun setDynamicColor(enabled: Boolean) = put(Keys.DYNAMIC_COLOR, enabled)

    suspend fun setPreferredQuality(quality: QualityFilter) =
        put(Keys.PREFERRED_QUALITY, quality.name)

    suspend fun setPreferredSort(sort: SortOption) = put(Keys.PREFERRED_SORT, sort.name)

    suspend fun setHapticFeedback(enabled: Boolean) = put(Keys.HAPTIC_FEEDBACK, enabled)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val PREFERRED_SORT = stringPreferencesKey("preferred_sort")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    }
}
