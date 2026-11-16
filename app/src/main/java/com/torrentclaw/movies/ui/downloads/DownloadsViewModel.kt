package com.torrentclaw.movies.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentclaw.movies.data.download.DownloadManager
import com.torrentclaw.movies.data.download.DownloadNotice
import com.torrentclaw.movies.data.local.DownloadEntity
import com.torrentclaw.movies.data.local.DownloadKind
import com.torrentclaw.movies.data.local.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Grouped, display-ready download state. */
data class DownloadsUiState(
    val active: List<DownloadEntity> = emptyList(),
    val completed: List<DownloadEntity> = emptyList(),
    val failed: List<DownloadEntity> = emptyList(),
    val totalCompletedBytes: Long = 0L,
) {
    val isEmpty: Boolean
        get() = active.isEmpty() && completed.isEmpty() && failed.isEmpty()

    val activeCount: Int
        get() = active.count {
            it.status == DownloadStatus.ACTIVE.name || it.status == DownloadStatus.QUEUED.name
        }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = combine(
        downloadManager.downloads,
        downloadManager.completedBytes,
    ) { all, bytes ->
        DownloadsUiState(
            active = all.filter {
                it.status == DownloadStatus.ACTIVE.name ||
                    it.status == DownloadStatus.QUEUED.name ||
                    it.status == DownloadStatus.PAUSED.name
            },
            completed = all.filter { it.status == DownloadStatus.COMPLETED.name },
            failed = all.filter { it.status == DownloadStatus.FAILED.name },
            totalCompletedBytes = bytes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.notices.collect { notice ->
                when (notice) {
                    is DownloadNotice.Completed ->
                        _message.value = "Saved ${notice.title}" + (notice.path?.let { " → $it" } ?: "")
                    is DownloadNotice.Failed ->
                        _message.value = "Download failed: ${notice.reason}"
                    is DownloadNotice.MagnetReady ->
                        _message.value = "Magnet ready for ${notice.title}"
                    null -> Unit
                }
                if (notice != null) downloadManager.consumeNotice()
            }
        }
    }

    fun pause(id: String) = downloadManager.pause(id)

    fun resume(id: String) = downloadManager.resume(id)

    fun retry(id: String) = downloadManager.retry(id)

    fun remove(id: String) = downloadManager.cancelAndDelete(id)

    fun clearFinished() = viewModelScope.launch { downloadManager.clearFinished() }

    /** Opens a finished `.torrent` in the user's client; returns false when none handled it. */
    fun openTorrent(entity: DownloadEntity): Boolean {
        if (entity.kind != DownloadKind.TORRENT_FILE.name) return false
        val opened = downloadManager.openTorrentFile(entity)
        if (!opened) {
            _message.value = "No torrent app could open this file. Install qBittorrent or Flud."
        }
        return opened
    }

    fun consumeMessage() {
        _message.value = null
    }
}
