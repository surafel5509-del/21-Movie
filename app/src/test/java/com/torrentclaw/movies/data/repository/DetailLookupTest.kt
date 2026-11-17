package com.torrentclaw.movies.data.repository

import com.torrentclaw.movies.data.local.CachedMovieDao
import com.torrentclaw.movies.network.ApiResult
import com.torrentclaw.movies.network.MagnetResolver
import com.torrentclaw.movies.network.TorrentClawApi
import com.torrentclaw.movies.network.dto.SearchResponseDto
import com.torrentclaw.movies.network.dto.SearchResultDto
import com.torrentclaw.movies.network.dto.TorrentDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Locks in how the detail screen resolves a movie by title.
 *
 * The bug being guarded against: the old implementation asked for **5** results
 * and matched `id` only inside that page. A title like "The Odyssey" matches
 * 1022 rows upstream, so the exact film could be missing from those 5 and the
 * screen silently degraded to cached metadata with zero releases — which is what
 * "No torrents indexed yet" looked like.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailLookupTest {

    private val api = mockk<TorrentClawApi>()
    private val cachedMovieDao = mockk<CachedMovieDao>(relaxed = true)
    private val magnetResolver = MagnetResolver()

    private fun result(
        id: Int,
        title: String,
        year: Int? = null,
        torrents: List<TorrentDto> = listOf(TorrentDto(infoHash = "hash$id")),
    ) = SearchResultDto(id = id, title = title, year = year, torrents = torrents)

    private fun ok(vararg items: SearchResultDto, total: Int = items.size) =
        Response.success(SearchResponseDto(total = total, pageSize = items.size, results = items.toList()))

    private fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        MovieRepositoryImpl(api, magnetResolver, cachedMovieDao, dispatcher)

    @Test
    fun `exact id on the first page resolves with release and asks for 50 rows`() = runTest {
        coEvery {
            api.getByContentUrl(any(), any(), any(), any(), any())
        } returns ok(result(1, "The Odyssey", 2026), result(2, "O Brother"))

        val apiResult = repository(UnconfinedTestDispatcher())
            .getMovieDetail(movieId = 1, knownTitle = "The Odyssey")

        assertTrue(apiResult is ApiResult.Success)
        assertEquals(1, (apiResult as ApiResult.Success).data.releases.size)
        // The old hardcoded limit was 5 — far too small for a common title.
        coVerify(exactly = 1) {
            api.getByContentUrl(title = "The Odyssey", limit = 50, page = 1)
        }
    }

    @Test
    fun `exact id only present on a later page is still found`() = runTest {
        // Page one is a wall of a same-titled remake; the real film is on page two.
        val page1 = (1..50).map { result(it, "The Odyssey", 1954) }
        coEvery {
            api.getByContentUrl(any(), any(), any(), page = 1, any())
        } returns ok(*page1.toTypedArray(), total = 51)
        coEvery {
            api.getByContentUrl(any(), any(), any(), page = 2, any())
        } returns ok(result(73263, "The Odyssey", 2026), total = 51)

        val apiResult = repository(UnconfinedTestDispatcher())
            .getMovieDetail(movieId = 73263, knownTitle = "The Odyssey")

        assertTrue(apiResult is ApiResult.Success)
        assertEquals(73263, (apiResult as ApiResult.Success).data.movie.id)
        coVerify(exactly = 1) { api.getByContentUrl(any(), any(), any(), page = 2, any()) }
    }

    @Test
    fun `paging stops once the upstream total is covered`() = runTest {
        coEvery {
            api.getByContentUrl(any(), any(), any(), any(), any())
        } returns ok(result(1, "Dune", 2021), total = 1)

        repository(UnconfinedTestDispatcher()).getMovieDetail(movieId = 1, knownTitle = "Dune")

        coVerify(exactly = 1) { api.getByContentUrl(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed lookup reports an error instead of an empty success`() = runTest {
        coEvery { api.getByContentUrl(any(), any(), any(), any(), any()) } returns Response.error(
            500,
            "boom".toResponseBody("application/json".toMediaType()),
        )

        val apiResult = repository(UnconfinedTestDispatcher())
            .getMovieDetail(movieId = 5, knownTitle = "Missing")

        // A Success with zero releases is the dead end that was reported.
        assertNotEquals(true, apiResult is ApiResult.Success)
        assertTrue((apiResult as ApiResult.Error).message.isNotBlank())
    }

    @Test
    fun `a same-title match with releases is used when the id is absent`() = runTest {
        // e.g. the user opened a watchlist row cached under an older id.
        coEvery {
            api.getByContentUrl(any(), any(), any(), any(), any())
        } returns ok(result(999, "The Odyssey", 2026))

        val apiResult = repository(UnconfinedTestDispatcher())
            .getMovieDetail(movieId = 73263, knownTitle = "The Odyssey")

        assertTrue(apiResult is ApiResult.Success)
        assertEquals(999, (apiResult as ApiResult.Success).data.movie.id)
    }
}
