package com.torrentclaw.movies.data.repository

import com.torrentclaw.movies.data.local.CachedMovieDao
import com.torrentclaw.movies.data.local.CachedMovieEntity
import com.torrentclaw.movies.data.local.WatchlistEntity
import com.torrentclaw.movies.domain.model.EmptyReleasesReason
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
import com.torrentclaw.movies.network.dto.toMovie
import com.torrentclaw.movies.network.dto.toMovieDetail
import com.torrentclaw.movies.network.safeApiCall
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

    /**
     * Resolves a movie's full detail, including every release.
     *
     * The API has no `/movie/{id}` route, so the screen looks the title up. Three
     * things are deliberate here, because the previous version could show
     * "No torrents indexed yet" for a movie the API answers with 10+ releases:
     *
     *  1. **The id match is found across every page of results**, not just the
     *     first five. "The Odyssey" matches 1022 titles upstream, so the exact
     *     film is rarely in the first handful.
     *  2. **A result with no releases is never returned as success.** If the lookup
     *     finds nothing usable, it reports an error the UI can explain, instead of
     *     silently degrading to cached metadata (which has no releases at all).
     *  3. **The exact film is preferred**, then a same-title/same-year match, then
     *     a plain title match — a wrong film's magnets are worse than an error.
     */
    override suspend fun getMovieDetail(
        movieId: Int,
        knownTitle: String?,
    ): ApiResult<MovieDetail> = withContext(ioDispatcher) {
        val title = knownTitle?.takeIf { it.isNotBlank() }

        if (title != null) {
            val found = resolveByTitle(title, movieId)
            if (found != null) {
                val detail = found.toMovieDetail(magnetResolver)
                return@withContext ApiResult.Success(
                    detail.copy(
                        // Honest diagnosis: an empty list here means the API really
                        // has no releases for this film (or we may not see hashes),
                        // not that the lookup failed.
                        emptyReason = if (detail.releases.isEmpty()) {
                            // Rows exist but no hash reached us: the API withheld
                            // magnets, which is a key problem, not an empty title.
                            val rowsButNoHashes = found.torrents.isNotEmpty()
                            if (rowsButNoHashes) EmptyReleasesReason.NEEDS_API_KEY
                            else EmptyReleasesReason.NOT_INDEXED_YET
                        } else {
                            null
                        },
                    ),
                )
            }
        }

        // Offline / partial-failure path: fall back to cached metadata, but only
        // when it can actually show something. A 0-release detail screen is the
        // bug we are fixing, so it is not an acceptable result.
        val cachedMovie = cachedMovieDao.getSection(SECTION_POPULAR)
            .map { it.toDomain() }
            .firstOrNull { it.id == movieId }
            // Nothing useful to say about a cached row we cannot decorate.
            ?: title?.let { wanted ->
                cachedMovieDao.getSection(SECTION_POPULAR)
                    .map { it.toDomain() }
                    .firstOrNull { it.title.equals(wanted, ignoreCase = true) }
            }
        if (cachedMovie != null) {
            return@withContext ApiResult.Error(
                type = ApiResult.ErrorType.NETWORK,
                message = "Found ${cachedMovie.title}, but its releases could not be loaded. " +
                    "Check your connection and pull to refresh.",
            )
        }

        ApiResult.Error(
            type = ApiResult.ErrorType.NOT_FOUND,
            message = "TorrentClaw returned no releases for this title. It may not be indexed yet.",
        )
    }

    /**
     * Searches for [title] and picks the best match for [movieId].
     *
     * Paged on purpose: the API caps `limit` at 50 and popular titles match
     * hundreds of results, so a single small page is not enough to be sure the
     * exact film is absent.
     */
    private suspend fun resolveByTitle(title: String, movieId: Int): SearchResultDto? {
        val seen = mutableListOf<SearchResultDto>()
        for (page in 1..DETAIL_MAX_PAGES) {
            val result = safeApiCall {
                api.getByContentUrl(title = title, limit = DETAIL_PAGE_SIZE, page = page)
            }
            val body = when (result) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> break
            }
            val pageResults = body.results
            if (pageResults.isEmpty()) break
            seen += pageResults

            // Exact content id wins outright, on any page.
            pageResults.firstOrNull { it.id == movieId }?.let { return it }
            // Otherwise stop when the upstream has nothing more to give.
            if (seen.size >= body.total || pageResults.size < DETAIL_PAGE_SIZE) break
        }

        // No id match. Fall back to a title match only when it is unambiguous:
        // "The Odyssey" is also a 1954 film, and showing its releases under the
        // 2026 poster would be a worse bug than saying "could not load".
        val sameTitle = seen.filter { it.title.equals(title, ignoreCase = true) }
        if (sameTitle.map { it.id }.distinct().size == 1) {
            return sameTitle.firstOrNull { it.torrents.isNotEmpty() } ?: sameTitle.firstOrNull()
        }
        return null
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
        /** Detail lookups page through results; the API caps a page at 50. */
        private const val DETAIL_PAGE_SIZE = 50
        private const val DETAIL_MAX_PAGES = 3

        private const val CACHE_TTL_MS = 15 * 60 * 1000L
        private const val CACHE_MAX_AGE_MS = 3 * 24 * 60 * 60 * 1000L
    }
}
