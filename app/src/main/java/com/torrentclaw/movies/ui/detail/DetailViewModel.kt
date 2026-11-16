package com.torrentclaw.movies.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.data.repository.MovieRepository
import com.torrentclaw.movies.data.repository.WatchlistRepository
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.MovieDetail
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.domain.model.TorrentRelease
import com.torrentclaw.movies.network.ApiResult
import com.torrentclaw.movies.network.dto.sortedFor
import com.torrentclaw.movies.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

data class DetailUiState(
    val isLoading: Boolean = true,
    val movieId: Int = 0,
    val title: String = "",
    val detail: MovieDetail? = null,
    val releases: List<TorrentRelease> = emptyList(),
    val releaseSort: SortOption = SortOption.SEEDERS,
    val isBookmarked: Boolean = false,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
) {
    val movie: Movie?
        get() = detail?.movie

    val hasReleases: Boolean
        get() = releases.isNotEmpty()

    val bestRelease: TorrentRelease?
        get() = releases.maxByOrNull { it.seeders }

    val totalSeeders: Int
        get() = releases.sumOf { it.seeders }
}

sealed interface DetailEffect {
    data class ShowSnackbar(val message: String) : DetailEffect
    data class OpenMagnet(val magnetUri: String) : DetailEffect
    data object MagnetCopied : DetailEffect
    data object NoTorrentClient : DetailEffect
}

/**
 * Detail screen state holder.
 *
 * The movie is addressed by [Destination.MovieDetail] (id + url-encoded title),
 * because TorrentClaw's detail lookup is title-based. Everything derives from
 * that one payload: metadata, bookmark state and the release list.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val movieRepository: MovieRepository,
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {

    private val movieId: Int = runCatching {
        when (val raw = savedStateHandle.get<Any?>(Destination.ARG_MOVIE_ID)) {
            is Int -> raw
            is Long -> raw.toInt()
            is String -> raw.toIntOrNull() ?: 0
            else -> 0
        }
    }.getOrDefault(0)

    private val title: String = savedStateHandle.get<String>(Destination.ARG_MOVIE_TITLE)
        ?.let { raw ->
            runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
        }
        .orEmpty()

    private val _uiState = MutableStateFlow(
        DetailUiState(movieId = movieId, title = title),
    )
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val effects = MutableStateFlow<DetailEffect?>(null)
    val effect: StateFlow<DetailEffect?> = effects.asStateFlow()

    init {
        viewModelScope.launch {
            watchlistRepository.observeIsBookmarked(movieId)
                .catch { emit(false) }
                .collect { saved -> _uiState.update { it.copy(isBookmarked = saved) } }
        }
        load(showSpinner = true)
    }

    fun load(showSpinner: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = showSpinner && it.detail == null,
                    isRefreshing = !showSpinner && it.detail != null,
                    errorMessage = null,
                )
            }

            when (val result = movieRepository.getMovieDetail(movieId, title)) {
                is ApiResult.Success -> {
                    val releases = result.data.releases.sortedFor(_uiState.value.releaseSort)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            detail = result.data,
                            releases = releases,
                            title = result.data.movie.title.ifBlank { title },
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun toggleBookmark() {
        val movie = _uiState.value.movie ?: return
        viewModelScope.launch {
            val nowSaved = watchlistRepository.toggle(movie)
            _uiState.update { it.copy(isBookmarked = nowSaved) }
            effects.value = DetailEffect.ShowSnackbar(
                if (nowSaved) "Added to watchlist" else "Removed from watchlist",
            )
        }
    }

    /** Snackbar text, raised by the screen after a bookmark toggle. */
    fun notifyBookmarkChange(saved: Boolean) {
        effects.value = DetailEffect.ShowSnackbar(
            if (saved) "Added to watchlist" else "Removed from watchlist",
        )
    }

    fun setReleaseSort(sort: SortOption) {
        _uiState.update { it.copy(releaseSort = sort, releases = it.releases.sortedFor(sort)) }
    }

    fun onOpenMagnet(release: TorrentRelease) {
        effects.value = DetailEffect.OpenMagnet(release.magnetUri)
        viewModelScope.launch {
            // Analytics ping; deliberately fire-and-forget so the UI never waits.
            movieRepository.trackMagnet(release.infoHash, "magnet")
        }
    }

    fun onOpenMagnetHandled(success: Boolean) {
        effects.value = null
        if (!success) effects.value = DetailEffect.NoTorrentClient
    }

    fun onMagnetOpened() {
        effects.value = null
    }

    fun onCopied(release: TorrentRelease) {
        viewModelScope.launch { movieRepository.trackMagnet(release.infoHash, "copy") }
        effects.value = DetailEffect.MagnetCopied
    }

    fun onNoClient() {
        effects.value = DetailEffect.NoTorrentClient
    }

    fun clearEffect() {
        effects.value = null
    }

    fun retry() = load(showSpinner = true)

    companion object {
        /** Encodes a title for the type-safe route. */
        fun encodeTitle(title: String): String =
            URLEncoder.encode(title, Charsets.UTF_8.name())
    }
}
