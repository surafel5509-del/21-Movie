package com.torrentclaw.movies.ui.search

import app.cash.turbine.test
import com.torrentclaw.movies.data.prefs.PreferencesRepository
import com.torrentclaw.movies.data.repository.MovieRepository
import com.torrentclaw.movies.data.repository.SearchPage
import com.torrentclaw.movies.data.repository.WatchlistRepository
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.domain.model.SearchFilters
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.domain.model.UserPreferences
import com.torrentclaw.movies.network.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val movieRepository: MovieRepository = mockk(relaxed = true)
    private val watchlistRepository: WatchlistRepository = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)

    private val sampleMovie = Movie(
        id = 1,
        title = "Dune: Part Two",
        originalTitle = null,
        overview = "Paul Atreides unites with the Fremen.",
        posterUrl = null,
        backdropUrl = null,
        year = 2024,
        ratingImdb = 8.5,
        ratingTmdb = 8.2,
        genres = listOf("Science Fiction"),
        contentUrl = null,
        imdbId = null,
        tmdbId = null,
        maxSeeders = 900,
        bestQuality = "4K",
        hasHdr = true,
        hasTorrents = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { watchlistRepository.observeWatchlist() } returns flowOf(emptyList())
        every { preferencesRepository.preferences } returns MutableStateFlow(UserPreferences())
        coEvery { movieRepository.search(any(), any(), any()) } returns ApiResult.Success(
            SearchPage(movies = listOf(sampleMovie), total = 1, page = 1, pageSize = 24),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SearchViewModel(
        movieRepository = movieRepository,
        watchlistRepository = watchlistRepository,
        preferencesRepository = preferencesRepository,
    )

    @Test
    fun `typing is debounced by 400ms before hitting the network`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SearchAction.QueryChanged("du"))
        advanceTimeBy(200)
        vm.onAction(SearchAction.QueryChanged("dun"))
        advanceTimeBy(200)
        vm.onAction(SearchAction.QueryChanged("dune"))

        // No request yet: the debounce window keeps resetting.
        advanceTimeBy(300)
        coVerify(exactly = 0) { movieRepository.search(any(), any(), any()) }

        advanceTimeBy(150)
        advanceUntilIdle()
        coVerify(exactly = 1) { movieRepository.search("dune", any(), any()) }
    }

    @Test
    fun `short queries never hit the network`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SearchAction.QueryChanged("d"))
        advanceUntilIdle()
        coVerify(exactly = 0) { movieRepository.search(any(), any(), any()) }
        assertFalse(vm.uiState.value.hasSearched)
    }

    @Test
    fun `successful search publishes results`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SearchAction.QueryChanged("dune"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(sampleMovie), state.movies)
        assertEquals(1, state.total)
        assertTrue(state.hasSearched)
        assertFalse(state.isLoading)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `rate limit error is surfaced with a dedicated flag`() = runTest(dispatcher) {
        coEvery { movieRepository.search(any(), any(), any()) } returns ApiResult.Error(
            type = ApiResult.ErrorType.RATE_LIMITED,
            message = "TorrentClaw is rate limiting us.",
        )
        val vm = viewModel()
        vm.onAction(SearchAction.QueryChanged("dune"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isRateLimited)
        assertEquals("TorrentClaw is rate limiting us.", state.errorMessage)
        assertTrue(state.movies.isEmpty())
    }

    @Test
    fun `quality filter is forwarded to the repository`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SearchAction.QueryChanged("dune"))
        advanceUntilIdle()
        vm.onAction(SearchAction.QualitySelected(QualityFilter.P2160))
        advanceUntilIdle()

        coVerify {
            movieRepository.search(
                "dune",
                withArg { assertEquals(QualityFilter.P2160, it.quality) },
                any(),
            )
        }
        assertEquals(SearchFilters(quality = QualityFilter.P2160), vm.uiState.value.filters)
    }

    @Test
    fun `sort selection re-runs the current query immediately`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SearchAction.QueryChanged("dune"))
        advanceUntilIdle()
        vm.onAction(SearchAction.SortSelected(SortOption.SIZE))
        advanceUntilIdle()

        assertEquals(SortOption.SIZE, vm.uiState.value.filters.sort)
        coVerify(atLeast = 2) { movieRepository.search("dune", any(), any()) }
    }

    @Test
    fun `clearing the query resets the result list`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SearchAction.QueryChanged("dune"))
        advanceUntilIdle()
        vm.onAction(SearchAction.ClearQuery)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.movies.isEmpty())
            assertFalse(state.hasSearched)
            assertEquals("", state.query)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
