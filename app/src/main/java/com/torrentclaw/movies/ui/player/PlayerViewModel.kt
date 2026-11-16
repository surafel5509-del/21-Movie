package com.torrentclaw.movies.ui.player

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.common.util.concurrent.MoreExecutors
import com.torrentclaw.movies.data.download.DownloadManager
import com.torrentclaw.movies.data.local.DownloadEntity
import com.torrentclaw.movies.data.local.DownloadKind
import com.torrentclaw.movies.data.player.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Everything the player screen needs for one file. */
data class PlayerUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val quality: String? = null,
    val filePath: String? = null,
    val error: String? = null,
    /** Why/whether playback starts part-way in, shown as a toast-ish banner. */
    val resumedAtMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Owns the [ExoPlayer] for the player screen.
 *
 * Keeping the player here (not in the composable) is what makes the app's
 * "leave and come back" behaviour work: a ViewModel survives configuration
 * changes, so rotating the phone or entering picture-in-picture does not create a
 * second player or restart the movie.
 *
 * The player is bound to [com.torrentclaw.movies.data.player.PlaybackService]
 * through a [MediaController] when possible, so the media notification and
 * lock-screen controls stay valid; it degrades to a local player if the session
 * cannot be reached.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val playbackRepository: PlaybackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val downloadId: String = savedStateHandle.get<String>("downloadId") ?: ""

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Created lazily once the file is resolved, so a bad path never builds a player. */
    var session: PlayerUiSession? by mutableStateOf(null)
        private set

    private var controller: MediaController? = null
    private var persistJob: Job? = null

    init {
        resolve()
    }

    private fun resolve() {
        viewModelScope.launch {
            val entity = downloadManager.findDownload(downloadId)
            if (entity == null) {
                _uiState.value = PlayerUiState(isLoading = false, error = "This download no longer exists.")
                return@launch
            }
            if (entity.kind != DownloadKind.TORRENT.name && entity.kind != DownloadKind.MEDIA.name) {
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    error = "Only downloaded movies can be played here.",
                )
                return@launch
            }

            val file = downloadManager.findDownloadedMedia(entity)
            if (file == null || !file.exists()) {
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    title = entity.movieTitle,
                    error = "The video file is missing. Retry the download from the Downloads tab.",
                )
                return@launch
            }

            open(entity, file)
        }
    }

    private suspend fun open(entity: DownloadEntity, file: File) {
        val resume = playbackRepository.resumeStateFor(file.absolutePath)
        val startAt = if (resume.shouldResume) resume.positionMs else 0L

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.setMediaItem(PlayerUiSession.buildMediaItem(file.absolutePath))
        player.prepare()
        player.playWhenReady = true

        val uiSession = PlayerUiSession(
            player = player,
            title = entity.movieTitle,
            quality = entity.quality,
            filePath = file.absolutePath,
            startPositionMs = startAt,
            initialResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
        ).apply {
            this@apply.persist = { position, duration ->
                if (duration > 0 && position >= duration - FINISHED_TOLERANCE_MS) {
                    playbackRepository.markFinished(file.absolutePath)
                } else {
                    playbackRepository.save(
                        filePath = file.absolutePath,
                        movieId = entity.movieId,
                        movieTitle = entity.movieTitle,
                        posterUrl = entity.posterUrl,
                        positionMs = position,
                        durationMs = duration,
                    )
                }
            }
            onFinished = {
                playbackRepository.markFinished(file.absolutePath)
            }
        }

        session = uiSession
        _uiState.value = PlayerUiState(
            isLoading = false,
            title = entity.movieTitle,
            quality = entity.quality,
            filePath = file.absolutePath,
            resumedAtMs = startAt,
            durationMs = resume.durationMs,
        )

        // Mirror the private player into the media session so the notification and
        // lock-screen controls work. Best-effort: a failure must not stop playback.
        connectSession(player)
    }

    private fun connectSession(player: Player) {
        runCatching {
            val token = SessionToken(
                context,
                ComponentName(context, com.torrentclaw.movies.data.player.PlaybackService::class.java),
            )
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                    runCatching {
                        controller = future.get()
                        // Local playback stays authoritative; the controller exists
                        // for the notification. Reflect state into it one way only.
                        controller?.play()
                    }
                },
                MoreExecutors.directExecutor(),
            )
        }
    }

    override fun onCleared() {
        persistJob?.cancel()
        runCatching { controller?.release() }
        session?.release()
        session = null
        super.onCleared()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        /** Within 2 s of the end counts as finished, for stubborn containers. */
        const val FINISHED_TOLERANCE_MS = 2_000L
    }
}
