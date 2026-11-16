package com.torrentclaw.movies.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.data.repository.WatchlistRepository
import com.torrentclaw.movies.domain.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WatchlistUiState {
    data object Loading : WatchlistUiState
    data class Success(val movies: List<Movie>) : WatchlistUiState
    data class Error(val message: String) : WatchlistUiState
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {

    val uiState: StateFlow<WatchlistUiState> = watchlistRepository.observeWatchlist()
        .map { movies -> WatchlistUiState.Success(movies) as WatchlistUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchlistUiState.Loading,
        )

    private val _removed = MutableStateFlow<Movie?>(null)

    /** Last removed movie, so the snackbar can offer an undo. */
    val removed: StateFlow<Movie?> = _removed.asStateFlow()

    fun remove(movie: Movie) {
        viewModelScope.launch {
            watchlistRepository.remove(movie.id)
            _removed.value = movie
        }
    }

    fun undoRemove() {
        val movie = _removed.value ?: return
        viewModelScope.launch {
            watchlistRepository.restore(movie)
            _removed.value = null
        }
    }

    fun clearRemoved() {
        _removed.value = null
    }
}
