package com.torrentclaw.movies.data.repository

import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.MovieDetail
import com.torrentclaw.movies.domain.model.SearchFilters
import com.torrentclaw.movies.network.ApiResult
import kotlinx.coroutines.flow.Flow

/** Network-backed movie discovery. Every call returns a safe [ApiResult]. */
interface MovieRepository {

    /** Trending movies (TorrentClaw `/popular`). Cached in Room for offline first paint. */
    suspend fun getPopular(
        limit: Int = 24,
        forceRefresh: Boolean = false,
    ): ApiResult<List<Movie>>

    /** Newly indexed movies (`/recent`). */
    suspend fun getRecent(limit: Int = 18): ApiResult<List<Movie>>

    /** Debounced search with quality/sort filters. */
    suspend fun search(
        query: String,
        filters: SearchFilters,
        page: Int = 1,
    ): ApiResult<SearchPage>

    /** Detail payload: metadata + all releases (magnet URIs resolved). */
    suspend fun getMovieDetail(
        movieId: Int,
        knownTitle: String?,
    ): ApiResult<MovieDetail>

    /** Fire-and-forget analytics for magnet interactions. Never throws. */
    suspend fun trackMagnet(infoHash: String, action: String)

    /** Cached popular list so the home screen can paint before the network answers. */
    fun observeCachedPopular(): Flow<List<Movie>>
}

data class SearchPage(
    val movies: List<Movie>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
) {
    val hasMore: Boolean
        get() = movies.isNotEmpty() && movies.size < total
}
