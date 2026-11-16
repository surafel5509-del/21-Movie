package com.torrentclaw.movies.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.torrentclaw.movies.BuildConfig
import com.torrentclaw.movies.data.local.DownloadDao
import com.torrentclaw.movies.data.local.DownloadEntity
import com.torrentclaw.movies.data.local.DownloadKind
import com.torrentclaw.movies.data.local.DownloadStatus
import com.torrentclaw.movies.data.torrent.TorrentEngine
import com.torrentclaw.movies.data.torrent.TorrentService
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
 * Two engines behind one queue:
 *  - **BitTorrent ([TorrentEngine])** does the actual movie downloads. A magnet
 *    joins the swarm in-process; [TorrentService] keeps it alive in the
 *    background and [observeTorrentEngine] mirrors live progress into Room.
 *  - **HTTP ([FileDownloader])** fetches `.torrent` files (so the user can hand
 *    one to a desktop client) and any direct media URL. A [Semaphore] bounds it
 *    to 2 concurrent transfers so mobile data and disk aren't saturated.
 *
 * State lives in Room; the manager only keeps [Job] handles in memory, so a
 * process restart resumes from the last persisted byte count.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val fileDownloader: FileDownloader,
    private val torrentEngine: TorrentEngine,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val engineWatchers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
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
    /**
     * Restarts work interrupted by process death: HTTP transfers from their
     * persisted byte offset, BitTorrent jobs by re-adding the magnet (libtorrent
     * re-checks the files already on disk and resumes from the verified pieces).
     */
    fun resumePending() {
        scope.launch {
            val pending = dao.getPending()
            pending.filter { it.kind == DownloadKind.TORRENT.name || it.kind == DownloadKind.MEDIA.name }
                .forEach { entity ->
                    // MEDIA/TORRENT jobs were interrupted mid-transfer; resume them.
                    if (entity.kind == DownloadKind.MEDIA.name) start(entity.id)
                }
            pending.filter { it.kind == DownloadKind.TORRENT.name && it.infoHash != null }
                .forEach { entity ->
                    // BitTorrent: rejoin the swarm with the persisted magnet.
                    val magnet = entity.magnetUri ?: return@forEach
                    resumeTorrent(
                        entity.copy(
                            magnetUri = magnet,
                            status = DownloadStatus.ACTIVE.name,
                        ),
                    )
                }
        }
    }

    // ─────────────────────── real movie downloads ────────────────────────

    /**
     * Starts a real BitTorrent download for [release].
     *
     * The row is persisted first so the queue survives process death, then the
     * engine is asked to join the swarm and the foreground service is started so
     * the transfer continues when the app is backgrounded. Progress flows back
     * from [TorrentEngine.progress] into Room, which is what the UI renders.
     *
     * Returns the row id, or null when the release carries no info hash (which
     * is what the anonymous tier sends back).
     */
    suspend fun enqueueTorrentDownload(
        movie: Movie,
        release: TorrentRelease,
    ): String? = withContext(Dispatchers.IO) {
        val infoHash = release.infoHash?.takeIf { it.isNotBlank() } ?: return@withContext null
        val magnet = release.playableMagnet ?: return@withContext null
        val id = DownloadEntity.movieTorrentId(infoHash)

        val existing = dao.findById(id)
        if (existing != null && existing.status == DownloadStatus.COMPLETED.name) {
            return@withContext id
        }

        val now = System.currentTimeMillis()
        val entity = DownloadEntity(
            id = id,
            kind = DownloadKind.TORRENT.name,
            movieId = movie.id,
            movieTitle = movie.title,
            posterUrl = movie.posterUrl,
            quality = release.qualityLabel,
            source = release.tracker,
            fileName = release.title ?: movie.title,
            url = magnet,
            magnetUri = magnet,
            infoHash = infoHash.uppercase(),
            totalBytes = release.sizeBytes ?: 0L,
            downloadedBytes = existing?.downloadedBytes ?: 0L,
            status = DownloadStatus.ACTIVE.name,
            localPath = downloadsDir().absolutePath,
            errorMessage = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        startTorrentSession()
        torrentEngine.addTorrent(release, downloadsDir())
        observeTorrentEngine(id, infoHash.uppercase(), release)
        id
    }

    /** Movies land in the app-specific external dir; no storage permission needed. */
    private fun downloadsDir(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Movies").apply { mkdirs() }

    /**
     * Starts the foreground service, which starts and owns the engine session.
     *
     * `startService()` throws `IllegalStateException` when it is called while the
     * app is in the background (which `resumePending()` does at process start) —
     * from API 26 a service intended to run in the foreground must be started
     * with `startForegroundService()`.
     */
    private fun startTorrentSession() {
        runCatching { TorrentService.start(context) }
            .onFailure { Log.w(TAG, "could not start the torrent service", it) }
    }

    /**
     * Bridges engine progress into Room for one download. Only the active rows
     * are updated, and only when a value actually changed, so the database write
     * rate stays at ~1 Hz instead of 60.
     */
    private fun observeTorrentEngine(
        id: String,
        infoHash: String,
        release: TorrentRelease? = null,
    ) {
        if (engineWatchers.add(id)) {
            scope.launch {
                torrentEngine.progress.collect { snapshot ->
                    val progress = snapshot[infoHash] ?: return@collect
                    val current = dao.findById(id) ?: return@collect
                    val status = when {
                        progress.isFinished -> DownloadStatus.COMPLETED.name
                        // A user pause must survive a progress tick, otherwise the
                        // row flips back to ACTIVE a second later.
                        progress.pausing -> DownloadStatus.PAUSED.name
                        else -> DownloadStatus.ACTIVE.name
                    }
                    if (current.downloadedBytes == progress.downloadedBytes &&
                        current.status == status
                    ) {
                        return@collect
                    }
                    dao.updateProgress(
                        id = id,
                        downloaded = progress.downloadedBytes,
                        total = progress.totalBytes.takeIf { it > 0 } ?: current.totalBytes,
                        status = status,
                        bps = progress.downloadRate,
                        eta = progress.etaSeconds,
                        now = System.currentTimeMillis(),
                    )
                    if (progress.isFinished) {
                        // Remember the exact file libtorrent wrote: scanning the
                        // save path later could pick a *different* movie.
                        val saved = progress.name
                        if (!saved.isNullOrBlank() && current.localPath != saved) {
                            dao.updateStatus(
                                id = id,
                                status = DownloadStatus.COMPLETED.name,
                                error = null,
                                localPath = saved,
                                now = System.currentTimeMillis(),
                            )
                        }
                        // Finished means any earlier stall warning is obsolete.
                        torrentEngine.clearError(infoHash)
                        if (current.status != DownloadStatus.COMPLETED.name) {
                            _notices.value = DownloadNotice.Completed(
                                id = id,
                                title = current.movieTitle,
                                path = current.localPath,
                            )
                        }
                    }
                }
            }
            scope.launch {
                torrentEngine.errors.collect { errors ->
                    val reason = errors[infoHash] ?: return@collect
                    // A finished download cannot be failed by a late stall report.
                    if (dao.findById(id)?.status == DownloadStatus.COMPLETED.name) return@collect
                    dao.updateStatus(
                        id = id,
                        status = DownloadStatus.FAILED.name,
                        error = reason,
                        localPath = null,
                        now = System.currentTimeMillis(),
                    )
                    _notices.value = DownloadNotice.Failed(
                        id = id,
                        title = dao.findById(id)?.movieTitle ?: "Download",
                        reason = reason,
                    )
                }
            }
        }
    }

    /** Pause/resume/remove a real torrent job (called from the UI). */
    fun pauseTorrent(entity: DownloadEntity) {
        scope.launch {
            entity.infoHash?.let { torrentEngine.pause(it.uppercase()) }
            dao.updateStatus(entity.id, DownloadStatus.PAUSED.name, null, entity.localPath, System.currentTimeMillis())
        }
    }

    /**
     * Resumes a torrent job. If libtorrent no longer knows the hash (fresh
     * process, or it was paused and removed), the magnet is re-added so the
     * swarm is rejoined and existing pieces are re-checked rather than re-fetched.
     */
    fun resumeTorrent(entity: DownloadEntity) {
        scope.launch {
            val infoHash = entity.infoHash?.uppercase() ?: return@launch
            dao.updateStatus(
                entity.id,
                DownloadStatus.ACTIVE.name,
                null,
                entity.localPath ?: downloadsDir().absolutePath,
                System.currentTimeMillis(),
            )
            startTorrentSession()
            if (torrentEngine.progress.value.containsKey(infoHash)) {
                torrentEngine.resume(infoHash)
            } else {
                val magnet = entity.magnetUri ?: return@launch
                val release = TorrentRelease(
                    infoHash = infoHash,
                    title = entity.fileName,
                    quality = entity.quality,
                    codec = null,
                    source = entity.source,
                    sizeBytes = entity.totalBytes.takeIf { it > 0 },
                    seeders = 0,
                    leechers = 0,
                    magnetUri = magnet,
                    torrentUrl = null,
                    tracker = entity.source,
                    qualityScore = null,
                    uploadedAt = null,
                    releaseGroup = null,
                    hdrType = null,
                    audioCodec = null,
                    languages = emptyList(),
                    isProper = false,
                    isRepack = false,
                )
                torrentEngine.addTorrent(release, downloadsDir())
                observeTorrentEngine(entity.id, infoHash, release)
            }
        }
    }

    fun removeTorrent(entity: DownloadEntity, deleteFiles: Boolean = false) {
        scope.launch {
            entity.infoHash?.let { hash ->
                torrentEngine.remove(hash.uppercase())
                if (deleteFiles) {
                    entity.fileName.takeIf { it.isNotBlank() }?.let { name ->
                        runCatching { File(downloadsDir(), name).deleteRecursively() }
                    }
                }
            }
            dao.deleteById(entity.id)
            engineWatchers.remove(entity.id)
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
    ): String? = withContext(Dispatchers.IO) {
        // No hash (anonymous tier) means no URL to fetch and nothing to hand to
        // a client; the UI disables the button in that case.
        val infoHash = release.infoHash?.takeIf { it.isNotBlank() } ?: return@withContext null
        val id = DownloadEntity.torrentId(infoHash)
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
            infoHash = infoHash,
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

    /**
     * Finds the queue row that produced [filePath]. Playback positions are keyed by
     * file, but the player route is addressed by download id, so this is the bridge
     * between a "Continue watching" card and a playable download.
     *
     * Matching is by containment because libtorrent writes into a folder named
     * after the torrent, one level below the recorded save path.
     */
    suspend fun downloadIdForFile(filePath: String): String? = withContext(Dispatchers.IO) {
        val target = runCatching { File(filePath).absolutePath }.getOrNull() ?: return@withContext null
        runCatching {
            dao.getCompletedAndActive().firstOrNull { entity ->
                val recorded = entity.localPath ?: return@firstOrNull false
                target == recorded || target.startsWith(recorded.trimEnd('/') + "/")
            }?.id
        }.getOrNull()
    }

    /** Looks up one queue row, for the player to resolve its file. */
    suspend fun findDownload(id: String): DownloadEntity? = withContext(Dispatchers.IO) {
        runCatching { dao.findById(id) }.getOrNull()
    }

    /** True when this download can be opened as a finished file. */
    fun localFileExists(entity: DownloadEntity): Boolean =
        entity.localPath?.let { resolveFile(it)?.exists() } == true

    /**
     * Opens a finished download: a `.torrent` goes to the user's client, a
     * downloaded movie goes to the video player. This is the payoff of the whole
     * pipeline, so it must work for the files the engine actually writes.
     */
    fun openDownloadedFile(entity: DownloadEntity): Boolean {
        if (entity.kind == DownloadKind.TORRENT_FILE.name) return openTorrentFile(entity)
        if (entity.kind != DownloadKind.TORRENT.name) return false

        val file = findDownloadedMedia(entity) ?: return false
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileDownloader.guessMimeType(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "no player could open ${file.name}", e)
            false
        }
    }

    /**
     * Finds the largest media file a torrent wrote. libtorrent creates the
     * torrent's own folder under the save path, so the file is nested at least
     * one level down and its real name is not the release title.
     */
    fun findDownloadedMedia(entity: DownloadEntity): File? {
        val root = downloadsDir()
        // Preferred: the torrent's own name, recorded when it finished.
        val named = entity.localPath?.takeIf { it.isNotBlank() && it != root.absolutePath }?.let {
            val direct = File(it)
            if (direct.isFile) return@let direct
            if (direct.isDirectory) return@let direct.walkTopDown()
                .filter { f -> f.isFile && FileDownloader.guessMimeType(f.name).startsWith("video/") }
                .maxByOrNull { f -> f.length() }
            null
        }
        if (named != null && named.exists()) return named

        // Fallbacks: the save path itself, or media whose name matches the release.
        if (root.exists()) {
            val media = root.walkTopDown()
                .filter { it.isFile && FileDownloader.guessMimeType(it.name).startsWith("video/") }
                .toList()
            val byName = media.firstOrNull { file ->
                entity.movieTitle.isNotBlank() &&
                    file.name.contains(entity.movieTitle, ignoreCase = true)
            }
            if (byName != null) return byName
            if (entity.localPath == root.absolutePath) media.maxByOrNull { it.length() }?.let { return it }
            // Last resort: only trust a media file whose parent names this torrent.
            entity.fileName?.takeIf { it.isNotBlank() }?.let { name ->
                media.firstOrNull { file ->
                    file.parentFile?.name?.contains(name, ignoreCase = true) == true
                }?.let { return it }
            }
        }
        // Not in the save path: fall back to the published shared folder.
        val published = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS,
            ),
            "TorrentClaw/${entity.fileName}",
        )
        return published.takeIf { it.exists() && it.isFile }
    }

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
        private const val TAG = "DownloadManager"
        /** Two concurrent transfers: enough to feel fast, gentle on mobile data. */
        private const val MAX_CONCURRENT = 2

        /** MIME used when handing a downloaded .torrent to a client. */
        const val TORRENT_MIME = "application/x-bittorrent"

    }
}
