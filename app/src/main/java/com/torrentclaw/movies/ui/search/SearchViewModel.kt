package com.torrentclaw.movies.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.data.prefs.PreferencesRepository
import com.torrentclaw.movies.data.repository.MovieRepository
import com.torrentclaw.movies.data.repository.WatchlistRepository
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.domain.model.SearchFilters
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val movies: List<Movie> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    val isRateLimited: Boolean = false,
    val bookmarkedIds: Set<Int> = emptySet(),
) {
    val isEmpty: Boolean
        get() = hasSearched && !isLoading && errorMessage == null && movies.isEmpty()

    val showIdle: Boolean
        get() = !hasSearched && !isLoading && query.isBlank()
}

sealed interface SearchAction {
    data class QueryChanged(val query: String) : SearchAction
    data class QualitySelected(val quality: QualityFilter) : SearchAction
    data class SortSelected(val sort: SortOption) : SearchAction
    data object ClearQuery : SearchAction
    data object ResetFilters : SearchAction
    data object Retry : SearchAction
}

/**
 * Search state machine.
 *
 * The 400 ms debounce is applied to the *query stream*, and each filter change
 * re-emits immediately (filters are discrete taps, so waiting would feel laggy).
 * `flatMapLatest` cancels the in-flight request when the user keeps typing,
 * which — together with the debounce — keeps the app inside TorrentClaw's
 * 120 req/min (30 anonymous) budget.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val watchlistRepository: WatchlistRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Raw text as typed — debounced before it reaches the network. */
    private val queryInput = MutableStateFlow("")

    /** Emits when filters change so the same query can be re-run instantly. */
    private val filterInput = MutableStateFlow(SearchFilters())
    private var searchJob: Job? = null

    /** Last query sent to the network; used to give chip taps a 0 ms debounce. */
    private var lastExecutedQuery: String = ""

    init {
        // Restore the user's default filters once, then let the chips own them.
        viewModelScope.launch {
            preferencesRepository.preferences
                .map { SearchFilters(quality = it.preferredQuality, sort = it.preferredSort) }
                .distinctUntilChanged()
                .collect { stored ->
                    if (_uiState.value.filters == SearchFilters() && stored != SearchFilters()) {
                        filterInput.value = stored
                        _uiState.update { it.copy(filters = stored) }
                    }
                }
        }

        viewModelScope.launch {
            watchlistRepository.observeWatchlist()
                .map { list -> list.map { it.id }.toSet() }
                .collect { ids -> _uiState.update { it.copy(bookmarkedIds = ids) } }
        }

        observeSearchInput()
    }

    private fun observeSearchInput() {
        searchJob = viewModelScope.launch {
            combine(queryInput, filterInput) { query, filters -> query to filters }
                .debounce { (query, _) ->
                    // Typing gets the full debounce; a chip tap is immediate.
                    if (query != lastExecutedQuery) DEBOUNCE_MILLIS else 0L
                }
                .filter { (query, _) -> query.isBlank() || query.trim().length >= MIN_QUERY_LENGTH }
                .distinctUntilChanged()
                .collect { (query, filters) -> executeSearch(query, filters) }
        }
    }

    private suspend fun executeSearch(query: String, filters: SearchFilters) {
        lastExecutedQuery = query
        val trimmed = query.trim()

        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    query = query,
                    filters = filters,
                    movies = emptyList(),
                    total = 0,
                    isLoading = false,
                    hasSearched = false,
                    errorMessage = null,
                    isRateLimited = false,
                )
            }
            return
        }
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(query = query, filters = filters, isLoading = false) }
            return
        }

        _uiState.update {
            it.copy(
                query = query,
                filters = filters,
                isLoading = true,
                errorMessage = null,
                isRateLimited = false,
                hasSearched = true,
            )
        }

        when (val result = movieRepository.search(trimmed, filters)) {
            is ApiResult.Success -> _uiState.update {
                it.copy(
                    movies = result.data.movies,
                    total = result.data.total,
                    isLoading = false,
                    hasSearched = true,
                    errorMessage = null,
                )
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    movies = emptyList(),
                    total = 0,
                    isLoading = false,
                    hasSearched = true,
                    errorMessage = result.message,
                    isRateLimited = result.type == ApiResult.ErrorType.RATE_LIMITED,
                )
            }
        }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> {
                _uiState.update { it.copy(query = action.query) }
                queryInput.value = action.query
            }
            is SearchAction.QualitySelected -> {
                val updated = _uiState.value.filters.copy(quality = action.quality)
                _uiState.update { it.copy(filters = updated) }
                filterInput.value = updated
                persistFilters(updated)
            }
            is SearchAction.SortSelected -> {
                val updated = _uiState.value.filters.copy(sort = action.sort)
                _uiState.update { it.copy(filters = updated) }
                filterInput.value = updated
                persistFilters(updated)
            }
            SearchAction.ClearQuery -> {
                queryInput.value = ""
                lastExecutedQuery = ""
                _uiState.update {
                    it.copy(
                        query = "",
                        movies = emptyList(),
                        total = 0,
                        hasSearched = false,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }
            SearchAction.ResetFilters -> {
                val defaults = SearchFilters()
                filterInput.value = defaults
                _uiState.update { it.copy(filters = defaults) }
                persistFilters(defaults)
            }
            SearchAction.Retry -> {
                val state = _uiState.value
                if (state.query.isNotBlank()) {
                    viewModelScope.launch { executeSearch(state.query, state.filters) }
                }
            }
        }
    }

    private fun persistFilters(filters: SearchFilters) {
        viewModelScope.launch {
            preferencesRepository.setPreferredQuality(filters.quality)
            preferencesRepository.setPreferredSort(filters.sort)
        }
    }

    companion object {
        /** Spec'd debounce so typing never trips the rate limiter. */
        const val DEBOUNCE_MILLIS = 400L
        const val MIN_QUERY_LENGTH = 2
    }
}
