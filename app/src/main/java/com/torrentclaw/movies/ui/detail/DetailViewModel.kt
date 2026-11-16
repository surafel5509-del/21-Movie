package com.torrentclaw.movies.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.data.download.DownloadManager
import com.torrentclaw.movies.data.torrent.TorrentEngine
import com.torrentclaw.movies.data.torrent.TorrentProgress
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
import kotlinx.coroutines.flow.first
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
    /** Live engine state keyed by upper-case info hash. */
    val torrentProgress: Map<String, TorrentProgress> = emptyMap(),
    /** Engine failures keyed by upper-case info hash (dead swarm, blocked P2P). */
    val torrentErrors: Map<String, String> = emptyMap(),
    /** True when the API is withholding hashes because no key is configured. */
    val needsApiKey: Boolean = false,
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
    data object TorrentQueued : DetailEffect
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
    private val downloadManager: DownloadManager,
    private val torrentEngine: TorrentEngine,
    apiKeyProvider: com.torrentclaw.movies.network.ApiKeyProvider,
) : ViewModel() {

    private val needsApiKey = !apiKeyProvider.isConfigured

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

        // Mirror engine progress into the detail state so a release card can show
        // its own live percentage without a separate screen.
        viewModelScope.launch {
            torrentEngine.progress.collect { snapshot ->
                _uiState.update { it.copy(torrentProgress = snapshot) }
            }
        }
        // Without this, a dead swarm or blocked BitTorrent traffic looks exactly
        // like a download that is merely slow: 0 % forever.
        viewModelScope.launch {
            torrentEngine.errors.collect { errors ->
                _uiState.update { it.copy(torrentErrors = errors) }
            }
        }
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
                            needsApiKey = needsApiKey && result.data.releases.any { !it.isDownloadable },
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
        val magnet = release.playableMagnet
        if (magnet == null) {
            notifyMissingMagnet()
            return
        }
        effects.value = DetailEffect.OpenMagnet(magnet)
        viewModelScope.launch {
            // Analytics ping; deliberately fire-and-forget so the UI never waits.
            release.infoHash?.let { movieRepository.trackMagnet(it, "magnet") }
        }
    }

    /** Anonymous tier: the release exists but carries no hash or magnet yet. */
    fun notifyMissingMagnet() {
        effects.value = DetailEffect.ShowSnackbar(
            "This release has no magnet link yet. Add a free TorrentClaw API key in Settings.",
        )
    }

    fun onOpenMagnetHandled(success: Boolean) {
        effects.value = null
        if (!success) effects.value = DetailEffect.NoTorrentClient
    }

    fun onMagnetOpened() {
        effects.value = null
    }

    /**
     * Starts a real BitTorrent download of the movie for [release].
     *
     * The engine swarms it, the foreground service keeps it alive in the
     * background, and progress is persisted to Room for the Downloads tab.
     */
    fun onDownloadMovie(release: TorrentRelease) {
        val movie = _uiState.value.movie ?: return
        viewModelScope.launch {
            runCatching { downloadManager.enqueueTorrentDownload(movie, release) }
                .onSuccess { id ->
                    effects.value = if (id != null) {
                        DetailEffect.TorrentQueued
                    } else {
                        DetailEffect.ShowSnackbar("This release has no info hash yet. Add an API key.")
                    }
                }
                .onFailure {
                    effects.value = DetailEffect.ShowSnackbar(
                        it.message ?: "Couldn't start the download.",
                    )
                }
        }
    }

    /** Stops a running movie download started from this screen. */
    fun onCancelMovieDownload(release: TorrentRelease) {
        viewModelScope.launch {
            val hash = release.infoHash?.uppercase() ?: return@launch
            downloadManager.downloads.first().firstOrNull { it.id == "movie:$hash" }?.let { entity ->
                downloadManager.removeTorrent(entity, deleteFiles = true)
            }
        }
    }

    /**
     * Queues the release's `.torrent` file for real, resumable downloading.
     * The file lands in Downloads/TorrentClaw and can then be opened in any
     * torrent client (see the Downloads tab).
     */
    fun onDownloadTorrent(release: TorrentRelease) {
        val movie = _uiState.value.movie ?: return
        viewModelScope.launch {
            runCatching { downloadManager.enqueueTorrentFile(movie, release) }
                .onSuccess { effects.value = DetailEffect.TorrentQueued }
                .onFailure {
                    effects.value = DetailEffect.ShowSnackbar(
                        it.message ?: "Couldn't start the download.",
                    )
                }
        }
    }

    fun onCopied(release: TorrentRelease) {
        release.infoHash?.let { hash ->
            viewModelScope.launch { movieRepository.trackMagnet(hash, "copy") }
        }
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
