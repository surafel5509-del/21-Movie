package com.torrentclaw.movies.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.torrentclaw.movies.BuildConfig
import com.torrentclaw.movies.data.local.DownloadDao
import com.torrentclaw.movies.data.local.DownloadEntity
import com.torrentclaw.movies.data.local.DownloadKind
import com.torrentclaw.movies.data.local.DownloadStatus
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.TorrentRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot messages the UI turns into snackbars. */
sealed interface DownloadNotice {
    data class Completed(val id: String, val title: String, val path: String?) : DownloadNotice
    data class Failed(val id: String, val title: String, val reason: String) : DownloadNotice
    data class MagnetReady(val title: String) : DownloadNotice
}

/**
 * Owns the download queue.
 *
 * Why a hand-rolled manager instead of WorkManager or a torrent engine:
 *  - TorrentClaw serves `.torrent` files over HTTPS, so getting one is a plain
 *    resumable HTTP transfer — no P2P stack required.
 *  - Running a BitTorrent client inside the app would mean shipping a native
 *    engine, a foreground service and a battery/thermal budget the project
 *    cannot responsibly claim to have tested. Media downloading is delegated to
 *    the user's installed client via the magnet/.torrent handoff.
 *  - A [Semaphore] bounds concurrency to 2 transfers so mobile data and disk
 *    aren't saturated.
 *
 * State lives in Room; the manager only keeps [Job] handles in memory, so a
 * process restart resumes from the last persisted byte count.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val fileDownloader: FileDownloader,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val throttle = Semaphore(MAX_CONCURRENT)

    private val _notices = MutableStateFlow<DownloadNotice?>(null)
    val notices: StateFlow<DownloadNotice?> = _notices.asStateFlow()

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)

    val activeDownloads: Flow<List<DownloadEntity>> = dao.observeActive()
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)

    val completedBytes: Flow<Long> = dao.observeCompletedBytes()
        .catch { emit(0L) }
        .flowOn(Dispatchers.IO)

    val activeCount: Flow<Int> = dao.observeActive()
        .map { list -> list.count { it.status == DownloadStatus.ACTIVE.name || it.status == DownloadStatus.QUEUED.name } }
        .catch { emit(0) }
        .flowOn(Dispatchers.IO)

    /** Called once from the application/UI entry point to resume interrupted work. */
    fun resumePending() {
        scope.launch {
            dao.getPending().forEach { entity ->
                if (entity.status == DownloadStatus.QUEUED.name) start(entity.id)
            }
        }
    }

    // ─────────────────────────── torrent files ───────────────────────────

    /**
     * Queues the `.torrent` file for [release]. Returns the download row id.
     * TorrentClaw exposes the file at `/api/v1/torrent/{infoHash}`.
     */
    suspend fun enqueueTorrentFile(
        movie: Movie,
        release: TorrentRelease,
    ): String = withContext(Dispatchers.IO) {
        val id = DownloadEntity.torrentId(release.infoHash)
        val existing = dao.findById(id)
        if (existing != null && existing.status == DownloadStatus.COMPLETED.name) return@withContext id

        val fileName = buildTorrentFileName(movie, release)
        val entity = DownloadEntity(
            id = id,
            kind = DownloadKind.TORRENT_FILE.name,
            movieId = movie.id,
            movieTitle = movie.title,
            posterUrl = movie.posterUrl,
            quality = release.qualityLabel,
            source = release.tracker,
            fileName = fileName,
            // Prefer the server-built torrent URL; fall back to the documented route.
            url = release.torrentUrl?.toAbsoluteUrl()
                ?: "${BuildConfig.TORRENTCLAW_BASE_URL}api/v1/torrent/${release.infoHash}",
            magnetUri = release.magnetUri,
            infoHash = release.infoHash,
            totalBytes = release.sizeBytes ?: 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED.name,
            localPath = null,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        start(id)
        id
    }

    // ───────────────────────────── media files ────────────────────────────

    /**
     * Queues a direct HTTP(S) media/progressive resource (e.g. a public-domain
     * film or a subtitles track). Rejects `magnet:` because that belongs to a
     * torrent client, not to this app.
     */
    suspend fun enqueueDirectMedia(
        movie: Movie,
        url: String,
        fileName: String,
        quality: String? = null,
    ): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "Only http(s) resources can be downloaded here"
        }
        val id = DownloadEntity.mediaId(url)
        val existing = dao.findById(id)
        if (existing != null && existing.status == DownloadStatus.COMPLETED.name) return@withContext id

        dao.upsert(
            DownloadEntity(
                id = id,
                kind = DownloadKind.MEDIA.name,
                movieId = movie.id,
                movieTitle = movie.title,
                posterUrl = movie.posterUrl,
                quality = quality,
                source = Uri.parse(url).host,
                fileName = fileName,
                url = url,
                magnetUri = null,
                infoHash = null,
                totalBytes = existing?.totalBytes ?: 0L,
                downloadedBytes = existing?.downloadedBytes ?: 0L,
                status = DownloadStatus.QUEUED.name,
                localPath = null,
                errorMessage = null,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        start(id)
        id
    }

    // ───────────────────────────── job control ────────────────────────────

    /** Starts (or resumes) the job for [id]. Safe to call repeatedly. */
    fun start(id: String) {
        if (jobs[id]?.isActive == true) return
        jobs[id] = scope.launch {
            val entity = dao.findById(id) ?: return@launch
            if (entity.status == DownloadStatus.COMPLETED.name) return@launch

            throttle.withPermit {
                val staging = fileDownloader.stagingFileFor(entity.fileName)
                val resumeFrom = if (staging.exists()) staging.length() else 0L

                dao.updateStatus(
                    id = id,
                    status = DownloadStatus.ACTIVE.name,
                    error = null,
                    localPath = entity.localPath,
                    now = System.currentTimeMillis(),
                )

                try {
                    val outcome = fileDownloader.download(
                        url = entity.url,
                        targetFileName = entity.fileName,
                        existingBytes = resumeFrom,
                        onProgress = { progress ->
                            // Persist so the UI and a post-restart resume see it.
                            dao.updateProgress(
                                id = id,
                                downloaded = progress.downloadedBytes,
                                total = progress.totalBytes,
                                status = DownloadStatus.ACTIVE.name,
                                bps = progress.bytesPerSecond,
                                eta = progress.etaSeconds,
                                now = System.currentTimeMillis(),
                            )
                        },
                    )

                    if (entity.kind == DownloadKind.TORRENT_FILE.name) {
                        // A .torrent belongs in shared Downloads where clients find it.
                        val published = runCatching {
                            fileDownloader.publishToDownloads(outcome.filePath, entity.fileName)
                        }.getOrElse { outcome.filePath }

                        dao.updateStatus(
                            id = id,
                            status = DownloadStatus.COMPLETED.name,
                            error = null,
                            localPath = published,
                            now = System.currentTimeMillis(),
                        )
                        _notices.value = DownloadNotice.Completed(id, entity.movieTitle, published)
                    } else {
                        val published = fileDownloader.publishToDownloads(outcome.filePath, entity.fileName)
                        dao.updateStatus(
                            id = id,
                            status = DownloadStatus.COMPLETED.name,
                            error = null,
                            localPath = published,
                            now = System.currentTimeMillis(),
                        )
                        _notices.value = DownloadNotice.Completed(id, entity.movieTitle, published)
                    }
                } catch (e: CancellationException) {
                    dao.updateStatus(
                        id = id,
                        status = DownloadStatus.PAUSED.name,
                        error = null,
                        localPath = null,
                        now = System.currentTimeMillis(),
                    )
                    throw e
                } catch (e: IOException) {
                    dao.updateStatus(
                        id = id,
                        status = DownloadStatus.FAILED.name,
                        error = e.message ?: "Network error",
                        localPath = null,
                        now = System.currentTimeMillis(),
                    )
                    _notices.value = DownloadNotice.Failed(
                        id = id,
                        title = entity.movieTitle,
                        reason = e.message ?: "Network error",
                    )
                } catch (e: Exception) {
                    dao.updateStatus(
                        id = id,
                        status = DownloadStatus.FAILED.name,
                        error = e.message ?: "Unexpected error",
                        localPath = null,
                        now = System.currentTimeMillis(),
                    )
                    _notices.value = DownloadNotice.Failed(
                        id = id,
                        title = entity.movieTitle,
                        reason = e.message ?: "Unexpected error",
                    )
                }
            }
        }
    }

    /** Cancels the running job, keeping bytes on disk so a resume is possible. */
    fun pause(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        scope.launch {
            dao.updateStatus(
                id = id,
                status = DownloadStatus.PAUSED.name,
                error = null,
                localPath = null,
                now = System.currentTimeMillis(),
            )
        }
    }

    /** Resumes a paused/failed job from the bytes already on disk. */
    fun resume(id: String) = start(id)

    /** User-initiated retry of a failed job: restarts from the existing bytes. */
    fun retry(id: String) {
        jobs.remove(id)
        start(id)
    }

    /** Removes the row and any partial file. */
    fun cancelAndDelete(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        scope.launch {
            val entity = dao.findById(id)
            if (entity != null) {
                fileDownloader.discard(fileDownloader.stagingFileFor(entity.fileName).absolutePath)
            }
            dao.deleteById(id)
        }
    }

    suspend fun clearFinished() = withContext(Dispatchers.IO) { dao.clearFinished() }

    fun consumeNotice() {
        _notices.value = null
    }

    // ──────────────────────────── handoff to clients ──────────────────────

    /**
     * Opens a downloaded `.torrent` file in the user's torrent client.
     * FileProvider is required because the file lives in shared Downloads and
     * Android 7+ forbids `file://` URIs across processes.
     */
    fun openTorrentFile(entity: DownloadEntity): Boolean {
        val path = entity.localPath ?: return false
        return try {
            val file = resolveFile(path) ?: return false
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileDownloader.guessMimeType(entity.fileName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** True when this download can be opened as a finished file. */
    fun localFileExists(entity: DownloadEntity): Boolean =
        entity.localPath?.let { resolveFile(it)?.exists() } == true

    private fun resolveFile(path: String): File? {
        val direct = File(path)
        if (direct.exists()) return direct
        // Published to the shared Downloads folder on API 29+: re-resolve by name.
        val candidate = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS,
            ),
            "TorrentClaw/${File(path).name}",
        )
        return candidate.takeIf { it.exists() }
    }

    private fun buildTorrentFileName(movie: Movie, release: TorrentRelease): String {
        val title = movie.title.replace(Regex("[^A-Za-z0-9]+"), ".").trim('.')
        val year = movie.year?.let { "($it)" }.orEmpty()
        val quality = release.quality?.let { ".$it" }.orEmpty()
        val group = release.releaseGroup?.let { "-$it" }.orEmpty()
        return listOfNotNull(
            "TorrentClaw.com",
            "$title$year$quality$group".trim('.'),
        ).joinToString("-").replace("--", "-") + ".torrent"
    }

    private fun String.toAbsoluteUrl(): String =
        if (startsWith("http")) this else BuildConfig.TORRENTCLAW_BASE_URL.trimEnd('/') + "/" + trimStart('/')

    companion object {
        /** Two concurrent transfers: enough to feel fast, gentle on mobile data. */
        private const val MAX_CONCURRENT = 2

        /** MIME used when handing a downloaded .torrent to a client. */
        const val TORRENT_MIME = "application/x-bittorrent"

    }
}
