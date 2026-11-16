package com.torrentclaw.movies.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Jetpack Compose destinations (Navigation 2.8+ serialization API).
 * No string route building, no argument-name typos.
 */
sealed interface Destination {

    @Serializable
    data object Home : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data object Watchlist : Destination

    @Serializable
    data object Downloads : Destination

    @Serializable
    data object Settings : Destination

    /**
     * Full-screen player for a downloaded file, addressed by download row id.
     *
     * Not a bottom-bar destination: it takes over the screen, hides the bars and
     * is the surface that goes into picture-in-picture.
     */
    @Serializable
    data class Player(val downloadId: String) : Destination

    /** Detail is addressed by content id + title (TorrentClaw looks content up by title). */
    @Serializable
    data class MovieDetail(
        val movieId: Int,
        val movieTitle: String,
    ) : Destination

    companion object {
        const val ARG_MOVIE_ID = "movieId"
        const val ARG_MOVIE_TITLE = "movieTitle"
    }
}

/** The three primary tabs, in bottom-bar order. */
enum class TopLevelDestination(
    val destination: Destination,
    val label: String,
    val route: String,
) {
    HOME(Destination.Home, "Discover", "home"),
    SEARCH(Destination.Search, "Search", "search"),
    WATCHLIST(Destination.Watchlist, "Watchlist", "watchlist"),
    DOWNLOADS(Destination.Downloads, "Downloads", "downloads"),
}
