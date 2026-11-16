package com.torrentclaw.movies.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.data.prefs.PreferencesRepository
import com.torrentclaw.movies.data.repository.MovieRepository
import com.torrentclaw.movies.data.repository.WatchlistRepository
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Home screen can render, modelled as a single immutable state. */
data class HomeUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val featured: Movie? = null,
    val popular: List<Movie> = emptyList(),
    val recent: List<Movie> = emptyList(),
    val bookmarkedIds: Set<Int> = emptySet(),
    val preferredQuality: QualityFilter = QualityFilter.ALL,
    val errorMessage: String? = null,
    val isOfflineNotice: Boolean = false,
)

sealed interface HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val watchlistRepository: WatchlistRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    private val bookmarkedIds: StateFlow<Set<Int>> = watchlistRepository.observeWatchlist()
        .map { movies -> movies.map { it.id }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val preferredQuality: StateFlow<QualityFilter> = preferencesRepository.preferences
        .map { it.preferredQuality }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, QualityFilter.ALL)

    init {
        // Mirror bookmarks/quality into the single UI state so composables read one object.
        viewModelScope.launch {
            combine(bookmarkedIds, preferredQuality) { ids, quality -> ids to quality }
                .collect { (ids, quality) ->
                    _uiState.value = _uiState.value.copy(
                        bookmarkedIds = ids,
                        preferredQuality = quality,
                    )
                }
        }
        load(forceRefresh = false)
    }

    /** Pull-to-refresh entry point. */
    fun refresh() = load(forceRefresh = true)

    fun retry() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(
                isInitialLoading = current.popular.isEmpty(),
                isRefreshing = forceRefresh,
                errorMessage = null,
                isOfflineNotice = false,
            )

            // Popular and recent are independent: fetch in parallel to halve the
            // latency, but never let one failure blank the other section.
            val popularDeferred = async { movieRepository.getPopular(forceRefresh = forceRefresh) }
            val recentDeferred = async { movieRepository.getRecent() }

            val popularResult = popularDeferred.await()
            val recentResult = recentDeferred.await()

            val popular = popularResult.getOrNull().orEmpty()
            val recent = recentResult.getOrNull().orEmpty()
            val error = (popularResult as? ApiResult.Error) ?: (recentResult as? ApiResult.Error)

            _uiState.value = _uiState.value.copy(
                isInitialLoading = false,
                isRefreshing = false,
                popular = popular,
                recent = recent,
                featured = popular.firstOrNull(),
                // Cached data + a failed refresh → non-blocking notice instead of an error screen.
                errorMessage = error?.message?.takeIf { popular.isEmpty() && recent.isEmpty() },
                isOfflineNotice = error != null && (popular.isNotEmpty() || recent.isNotEmpty()),
            )

            if (error != null && (popular.isNotEmpty() || recent.isNotEmpty())) {
                _events.value = HomeEvent.ShowMessage("Showing saved results — ${error.message}")
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }
}
