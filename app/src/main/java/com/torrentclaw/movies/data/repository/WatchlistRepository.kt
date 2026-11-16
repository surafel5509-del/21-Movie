package com.torrentclaw.movies.data.repository

import com.torrentclaw.movies.domain.model.Movie
import kotlinx.coroutines.flow.Flow

/** Room-backed bookmarks. Available fully offline. */
interface WatchlistRepository {

    fun observeWatchlist(): Flow<List<Movie>>

    fun observeIsBookmarked(movieId: Int): Flow<Boolean>

    suspend fun isBookmarked(movieId: Int): Boolean

    /** Adds or removes the bookmark and reports the resulting state. */
    suspend fun toggle(movie: Movie): Boolean

    suspend fun remove(movieId: Int)

    /** Re-adds a previously removed movie (used by the Watchlist "Undo" action). */
    suspend fun restore(movie: Movie)

    suspend fun count(): Int
}
