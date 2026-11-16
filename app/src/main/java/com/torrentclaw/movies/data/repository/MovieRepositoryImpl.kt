package com.torrentclaw.movies.data.repository

import com.torrentclaw.movies.data.local.CachedMovieDao
import com.torrentclaw.movies.data.local.CachedMovieEntity
import com.torrentclaw.movies.data.local.WatchlistEntity
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.MovieDetail
import com.torrentclaw.movies.domain.model.SearchFilters
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.network.ApiResult
import com.torrentclaw.movies.network.MagnetResolver
import com.torrentclaw.movies.network.TorrentClawApi
import com.torrentclaw.movies.network.dto.PopularItemDto
import com.torrentclaw.movies.network.dto.RecentItemDto
import com.torrentclaw.movies.network.dto.SearchResultDto
import com.torrentclaw.movies.network.dto.TrackRequestDto
import com.torrentclaw.movies.network.safeApiCall
import com.torrentclaw.movies.network.dto.toMovie
import com.torrentclaw.movies.network.dto.toMovieDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default movie repository.
 *
 * Responsibilities:
 *  - call TorrentClaw through the safe wrapper (no raw exceptions escape),
 *  - keep the popular list in Room so Home paints instantly and works offline,
 *  - resolve every release to a usable magnet URI,
 *  - apply the client-side "Size" sort the API does not support,
 *  - fall back to cached data when the network fails.
 */
@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val api: TorrentClawApi,
    private val magnetResolver: MagnetResolver,
    private val cachedMovieDao: CachedMovieDao,
    private val ioDispatcher: CoroutineDispatcher,
) : MovieRepository {

    override suspend fun getPopular(limit: Int, forceRefresh: Boolean): ApiResult<List<Movie>> =
        withContext(ioDispatcher) {
            val cachedEntities = cachedMovieDao.getSection(SECTION_POPULAR)
            val cached = cachedEntities.map { it.toDomain() }
            val cacheAge = System.currentTimeMillis() - (cachedEntities.firstOrNull()?.cachedAt ?: 0L)
            val cacheIsFresh = cached.isNotEmpty() && cacheAge in 0 until CACHE_TTL_MS

            if (cacheIsFresh && !forceRefresh) return@withContext ApiResult.Success(cached)

            when (val result = safeApiCall { api.getPopular(limit = limit.coerceIn(1, 24)) }) {
                is ApiResult.Success -> {
                    val movies = result.data.items
                        .asSequence()
                        .filter { it.isMovie() }
                        .map { it.toMovie() }
                        .filter { it.title.isNotBlank() }
                        .distinctBy { it.id }
                        .toList()
                    if (movies.isNotEmpty()) {
                        persistSection(movies, SECTION_POPULAR)
                        ApiResult.Success(movies)
                    } else if (cached.isNotEmpty()) {
                        ApiResult.Success(cached)
                    } else {
                        ApiResult.Error(
                            type = ApiResult.ErrorType.UNKNOWN,
                            message = "TorrentClaw hasn't published any popular movies yet.",
                        )
                    }
                }
                // Network failed: cached results beat an error screen.
                is ApiResult.Error -> if (cached.isNotEmpty()) ApiResult.Success(cached) else result
            }
        }

    override suspend fun getRecent(limit: Int): ApiResult<List<Movie>> = withContext(ioDispatcher) {
        safeApiCall { api.getRecent(limit = limit.coerceIn(1, 24)) }.map { dto ->
            dto.items
                .asSequence()
                .filter { it.isMovie() }
                .map { it.toMovie() }
                .filter { it.title.isNotBlank() }
                .distinctBy { it.id }
                .toList()
        }
    }

    override suspend fun search(
        query: String,
        filters: SearchFilters,
        page: Int,
    ): ApiResult<SearchPage> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            return@withContext ApiResult.Success(
                SearchPage(movies = emptyList(), total = 0, page = 1, pageSize = 0),
            )
        }

        // "Size" has no server-side equivalent: fetch by relevance with the same
        // filters, then re-rank locally on the fields we can see.
        val apiSort = if (filters.sort.clientSide) "relevance" else filters.sort.apiValue

        safeApiCall {
            api.search(
                query = trimmed,
                quality = filters.quality.apiValue,
                sort = apiSort,
                page = page,
            )
        }.map { response ->
            val movies = response.results
                .asSequence()
                .filter { it.isMovie() }
                .map { it.toMovie() }
                .filter { it.title.isNotBlank() }
                .distinctBy { it.id }
                .toList()
                .let { list ->
                    if (filters.sort == SortOption.SIZE) {
                        list.sortedByDescending { it.maxSeeders }
                    } else {
                        list
                    }
                }
            SearchPage(
                movies = movies,
                total = if (response.total > 0) response.total else movies.size,
                page = response.page.coerceAtLeast(page),
                pageSize = response.pageSize,
            )
        }
    }

    override suspend fun getMovieDetail(
        movieId: Int,
        knownTitle: String?,
    ): ApiResult<MovieDetail> = withContext(ioDispatcher) {
        // TorrentClaw exposes content by id through search/contentUrl, not a
        // /movie/{id} route, so the detail screen looks the title up and then
        // pins the exact id.
        val title = knownTitle?.takeIf { it.isNotBlank() }

        if (title != null) {
            when (val byTitle = safeApiCall { api.getByContentUrl(title = title) }) {
                is ApiResult.Success -> {
                    val match = byTitle.data.results.firstOrNull { it.id == movieId }
                        ?: byTitle.data.results.firstOrNull { it.title.equals(title, ignoreCase = true) }
                    if (match != null) {
                        return@withContext ApiResult.Success(match.toMovieDetail(magnetResolver))
                    }
                }
                is ApiResult.Error -> {
                    // Keep the error; the cached/id lookups below are cheap and offline.
                }
            }
        }

        // Offline / partial-failure path: fall back to cached metadata.
        val cachedMovie = cachedMovieDao.getSection(SECTION_POPULAR)
            .map { it.toDomain() }
            .firstOrNull { it.id == movieId }
        if (cachedMovie != null) {
            return@withContext ApiResult.Success(MovieDetail(movie = cachedMovie, releases = emptyList()))
        }

        ApiResult.Error(
            type = ApiResult.ErrorType.NOT_FOUND,
            message = "We couldn't find this movie on TorrentClaw.",
        )
    }

    override suspend fun trackMagnet(infoHash: String, action: String) {
        withContext(ioDispatcher) {
            runCatching {
                api.track(TrackRequestDto(infoHash = infoHash, action = action))
            }
        }
    }

    override fun observeCachedPopular(): Flow<List<Movie>> =
        cachedMovieDao.observeSection(SECTION_POPULAR)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { emit(emptyList()) }
            .flowOn(ioDispatcher)

    private suspend fun persistSection(movies: List<Movie>, section: String) {
        val now = System.currentTimeMillis()
        cachedMovieDao.clearSection(section)
        cachedMovieDao.upsertAll(movies.mapIndexed { index, movie -> movie.toCacheEntity(section, index, now) })
        cachedMovieDao.evictOlderThan(now - CACHE_MAX_AGE_MS)
    }

    private fun Movie.toCacheEntity(section: String, position: Int, now: Long) = CachedMovieEntity(
        id = id,
        section = section,
        position = position,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        ratingImdb = ratingImdb,
        ratingTmdb = ratingTmdb,
        genres = genres.joinToString(WatchlistEntity.GENRE_DELIMITER),
        contentUrl = contentUrl,
        imdbId = imdbId,
        tmdbId = tmdbId,
        maxSeeders = maxSeeders,
        bestQuality = bestQuality,
        hasHdr = hasHdr,
        cachedAt = now,
    )

    /** The API mixes movies and shows; this app is movie-only. */
    private fun PopularItemDto.isMovie(): Boolean =
        contentType.isNullOrBlank() || contentType.equals(CONTENT_TYPE_MOVIE, ignoreCase = true)

    private fun RecentItemDto.isMovie(): Boolean =
        contentType.isNullOrBlank() || contentType.equals(CONTENT_TYPE_MOVIE, ignoreCase = true)

    private fun SearchResultDto.isMovie(): Boolean =
        contentType.isBlank() || contentType.equals(CONTENT_TYPE_MOVIE, ignoreCase = true)

    companion object {
        const val SECTION_POPULAR = "popular"
        const val MIN_QUERY_LENGTH = 2
        private const val CONTENT_TYPE_MOVIE = "movie"
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
        private const val CACHE_MAX_AGE_MS = 3 * 24 * 60 * 60 * 1000L
    }
}
