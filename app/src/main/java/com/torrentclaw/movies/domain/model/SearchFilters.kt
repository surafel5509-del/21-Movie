package com.torrentclaw.movies.domain.model

/**
 * Resolution filter. [apiValue] is what the TorrentClaw `quality` parameter accepts.
 */
enum class QualityFilter(val label: String, val apiValue: String?) {
    ALL("All", null),
    P720("720p", "720p"),
    P1080("1080p", "1080p"),
    P2160("2160p", "2160p"),
    ;

    companion object {
        fun fromStorage(value: String?): QualityFilter =
            entries.firstOrNull { it.name == value } ?: ALL
    }
}

/**
 * Sort order. TorrentClaw supports relevance | seeders | year | rating | added.
 * "Size" is not a server-side sort, so it is applied client-side by the
 * repository after the response arrives (see [clientSide]).
 */
enum class SortOption(
    val label: String,
    val apiValue: String,
    val defaultDescending: Boolean = true,
) {
    SEEDERS("Seeders", "seeders"),
    NEWEST("Newest", "added"),
    SIZE("Size", "seeders", defaultDescending = true),
    ;

    /** TorrentClaw has no size sort; those results are re-sorted on device. */
    val clientSide: Boolean
        get() = this == SIZE

    companion object {
        fun fromStorage(value: String?): SortOption =
            entries.firstOrNull { it.name == value } ?: SEEDERS
    }
}

/**
 * Immutable filter state for the search screen. Kept in the ViewModel and
 * persisted through DataStore so the choice survives process death.
 */
data class SearchFilters(
    val quality: QualityFilter = QualityFilter.ALL,
    val sort: SortOption = SortOption.SEEDERS,
) {
    val isDefault: Boolean
        get() = quality == QualityFilter.ALL && sort == SortOption.SEEDERS
}
