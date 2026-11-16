package com.torrentclaw.movies.domain.model

/** How the app decides between dark (cinema) and light palettes. */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: DARK
    }
}

/**
 * Everything the user can change, persisted in a single Preferences DataStore.
 * Dark mode stays the default because the app is built around poster art.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val preferredQuality: QualityFilter = QualityFilter.ALL,
    val preferredSort: SortOption = SortOption.SEEDERS,
    val hapticFeedback: Boolean = true,
) {
    val filters: SearchFilters
        get() = SearchFilters(quality = preferredQuality, sort = preferredSort)
}
