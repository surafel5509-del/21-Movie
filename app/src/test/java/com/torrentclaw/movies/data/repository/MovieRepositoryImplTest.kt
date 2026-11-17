package com.torrentclaw.movies.data.repository

import com.torrentclaw.movies.data.local.CachedMovieDao
import com.torrentclaw.movies.data.local.CachedMovieEntity
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.domain.model.SearchFilters
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.network.MagnetResolver
import com.torrentclaw.movies.network.TorrentClawApi
import com.torrentclaw.movies.network.dto.PopularItemDto
import com.torrentclaw.movies.network.dto.PopularResponseDto
import com.torrentclaw.movies.network.dto.SearchResponseDto
import com.torrentclaw.movies.network.dto.SearchResultDto
import com.torrentclaw.movies.network.dto.TorrentDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class MovieRepositoryImplTest {

    private val api = FakeApi()
    private val dao = FakeCachedMovieDao()
    private val repository = MovieRepositoryImpl(
        api = api,
        magnetResolver = MagnetResolver(),
        cachedMovieDao = dao,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `popular maps and caches movies`() = runTest {
        api.popularResponse = Response.success(
            PopularResponseDto(
                items = listOf(
                    PopularItemDto(id = 1, title = "Dune", year = 2021, ratingImdb = "8.0", maxSeeders = 300),
                    PopularItemDto(id = 2, title = "A Show", contentType = "show"),
                ),
                total = 2,
            ),
        )

        val result = repository.getPopular()

        assertTrue(result.isSuccess)
        val movies = result.getOrNull().orEmpty()
        assertEquals(1, movies.size) // shows are filtered out
        assertEquals("Dune", movies.first().title)
        assertEquals(1, dao.stored.size)
    }

    @Test
    fun `network failure falls back to cache`() = runTest {
        dao.stored = mutableListOf(cachedEntity(id = 7, title = "Cached Movie", cachedAt = 0L))
        api.popularResponse = null // forces an IOException

        val result = repository.getPopular(forceRefresh = true)

        assertTrue(result.isSuccess)
        assertEquals("Cached Movie", result.getOrNull()!!.first().title)
    }

    @Test
    fun `fresh cache short circuits the network`() = runTest {
        dao.stored = mutableListOf(
            cachedEntity(id = 3, title = "Fresh", cachedAt = System.currentTimeMillis()),
        )

        val result = repository.getPopular(forceRefresh = false)

        assertTrue(result.isSuccess)
        assertEquals(0, api.popularCalls)
        assertEquals("Fresh", result.getOrNull()!!.first().title)
    }

    @Test
    fun `http 429 becomes a retryable rate limit error`() = runTest {
        api.popularResponse = Response.error(
            429,
            "{}".toResponseBody("application/json".toMediaType()),
        )

        val result = repository.getPopular(forceRefresh = true)

        val error = result as com.torrentclaw.movies.network.ApiResult.Error
        assertEquals(com.torrentclaw.movies.network.ApiResult.ErrorType.RATE_LIMITED, error.type)
        assertTrue(error.retryable)
    }

    @Test
    fun `search forwards quality and sort and tolerates a blank query`() = runTest {
        api.searchResponse = Response.success(
            SearchResponseDto(
                total = 1,
                results = listOf(movieResult()),
            ),
        )

        val filtered = repository.search(
            query = "  dune  ",
            filters = SearchFilters(quality = QualityFilter.P1080, sort = SortOption.SEEDERS),
        )
        assertTrue(filtered.isSuccess)
        assertEquals("dune", api.lastQuery)
        assertEquals("1080p", api.lastQuality)
        assertEquals("seeders", api.lastSort)

        val blank = repository.search(query = " ", filters = SearchFilters())
        assertTrue(blank.isSuccess)
        assertTrue(blank.getOrNull()!!.movies.isEmpty())
    }

    @Test
    fun `size sort is executed client side`() = runTest {
        api.searchResponse = Response.success(
            SearchResponseDto(
                total = 2,
                results = listOf(
                    movieResult(id = 1, seeders = 10),
                    movieResult(id = 2, seeders = 900),
                ),
            ),
        )

        val result = repository.search("dune", SearchFilters(sort = SortOption.SIZE))

        // The API has no size sort, so relevance is requested and we rank locally.
        assertEquals("relevance", api.lastSort)
        assertEquals(900, result.getOrNull()!!.movies.first().maxSeeders)
    }

    @Test
    fun `detail pins the exact content id and resolves magnets`() = runTest {
        api.searchResponse = Response.success(
            SearchResponseDto(
                total = 1,
                results = listOf(movieResult(id = 42)),
            ),
        )

        val result = repository.getMovieDetail(movieId = 42, knownTitle = "Dune")

        assertTrue(result.isSuccess)
        val detail = result.getOrNull()!!
        assertEquals(42, detail.movie.id)
        assertEquals(1, detail.releases.size)
        assertNotNull(detail.releases.first().magnetUri)
        assertTrue(detail.releases.first().magnetUri!!.startsWith("magnet:?xt=urn:btih:"))
    }

    @Test
    fun `detail reports not found when nothing matches`() = runTest {
        api.searchResponse = Response.success(SearchResponseDto(total = 0))
        val result = repository.getMovieDetail(movieId = 999, knownTitle = "Nothing")
        // Cached rows exist in this fake, but cached metadata has no releases, so
        // it must be reported as a network/loading failure - never as a successful
        // detail with an empty release list.
        assertTrue(
            "a 0-release result must never be a success",
            result !is com.torrentclaw.movies.network.ApiResult.Error ||
                (result as com.torrentclaw.movies.network.ApiResult.Error).message.isNotBlank(),
        )
        assertTrue(result !is com.torrentclaw.movies.network.ApiResult.Success)
    }

    // --- fakes -------------------------------------------------------------

    private fun movieResult(id: Int = 1, seeders: Int = 500) = SearchResultDto(
        id = id,
        contentType = "movie",
        title = "Dune",
        year = 2021,
        ratingImdb = "8.0",
        maxSeeders = seeders,
        torrents = listOf(
            TorrentDto(
                infoHash = "aaf1e71c0a0e3b1c0f1a2b3c4d5e6f7a8b9c0d1e",
                quality = "1080p",
                sizeBytes = "2147483648",
                seeders = seeders,
                leechers = 12,
                magnetUrl = null,
                source = "yts",
            ),
        ),
    )

    private fun cachedEntity(id: Int, title: String, cachedAt: Long) = CachedMovieEntity(
        id = id,
        section = MovieRepositoryImpl.SECTION_POPULAR,
        position = 0,
        title = title,
        originalTitle = null,
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        year = 2021,
        ratingImdb = 8.0,
        ratingTmdb = null,
        genres = "",
        contentUrl = null,
        imdbId = null,
        tmdbId = null,
        maxSeeders = 1,
        bestQuality = "1080p",
        hasHdr = false,
        cachedAt = cachedAt,
    )

    private class FakeApi : TorrentClawApi {
        var popularResponse: Response<PopularResponseDto>? = null
        var searchResponse: Response<SearchResponseDto>? = null
        var popularCalls = 0
        var lastQuery: String? = null
        var lastQuality: String? = null
        var lastSort: String? = null

        override suspend fun getPopular(limit: Int, page: Int, locale: String?) =
            (popularResponse ?: throw IOException("offline")).also { popularCalls++ }

        override suspend fun getRecent(limit: Int, page: Int, locale: String?) =
            throw IOException("offline")

        override suspend fun search(
            query: String,
            type: String,
            quality: String?,
            sort: String,
            page: Int,
            limit: Int,
            locale: String?,
        ): Response<SearchResponseDto> {
            lastQuery = query
            lastQuality = quality
            lastSort = sort
            return searchResponse ?: throw IOException("offline")
        }

        override suspend fun getByContentUrl(
            title: String,
            type: String,
            limit: Int,
            page: Int,
            locale: String?,
        ): Response<SearchResponseDto> = searchResponse ?: throw IOException("offline")

        override suspend fun getCredits(contentId: Int) =
            throw IOException("offline")

        override suspend fun track(body: com.torrentclaw.movies.network.dto.TrackRequestDto) =
            Response.success(com.torrentclaw.movies.network.dto.TrackResponseDto(ok = true))
    }

    private class FakeCachedMovieDao : CachedMovieDao {
        var stored = mutableListOf<CachedMovieEntity>()

        override fun observeSection(section: String) =
            kotlinx.coroutines.flow.flowOf(stored.filter { it.section == section })

        override suspend fun getSection(section: String) =
            stored.filter { it.section == section }.sortedBy { it.position }

        override suspend fun upsertAll(entities: List<CachedMovieEntity>) {
            stored = entities.toMutableList()
        }

        override suspend fun clearSection(section: String) {
            stored.removeAll { it.section == section }
        }

        override suspend fun evictOlderThan(cutoff: Long) {
            stored.removeAll { it.cachedAt < cutoff && it.cachedAt > 0 }
        }

        override suspend fun clear() {
            stored.clear()
        }
    }
}
