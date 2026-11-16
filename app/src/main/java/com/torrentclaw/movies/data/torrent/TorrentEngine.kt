package com.torrentclaw.movies.data.torrent

import android.util.Log
import com.torrentclaw.movies.domain.model.TorrentRelease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentAlert
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Live snapshot of one torrent, emitted roughly once a second while running. */
data class TorrentProgress(
    val infoHash: String,
    val state: String = "starting",
    val hasMetadata: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadRate: Long = 0L,
    val uploadRate: Long = 0L,
    val peers: Int = 0,
    val seeds: Int = 0,
    val etaSeconds: Long = -1L,
    val name: String? = null,
    val isFinished: Boolean = false,
    val pausing: Boolean = false,
)

/**
 * Real BitTorrent swarming through libtorrent (libtorrent4j).
 *
 * Design notes, because they are not obvious:
 *
 *  - **One thread owns the session.** libtorrent's session must be created,
 *    driven and destroyed from a single thread and every SWIG call blocks. All
 *    public methods hop onto a single-threaded executor and return immediately,
 *    so the UI never blocks.
 *  - **Progress is polled, not pushed.** libtorrent's alerts are unreliable for
 *    fine-grained progress (they are event-driven), so the ticker reads
 *    `handle.status()` once per second — that is the blessed path and it is
 *    cheap thanks to libtorrent4j's internal status cache.
 *  - **Resume needs no fastresume file.** libtorrent re-checks the files it
 *    finds under the save path and resumes from the verified pieces, so a
 *    process death costs a check, not a re-download.
 *  - **Only media is fetched.** On metadata arrival every file gets [Priority.SKIP]
 *    except video/subtitle extensions, so samples and .nfo/.exe junk are never
 *    downloaded.
 */
@Singleton
class TorrentEngine @Inject constructor() {

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "torrent-engine").apply { isDaemon = true }
        }

    private var manager: SessionManager? = null
    private var running = false
    private var tickerStarted = false

    private val _progress = MutableStateFlow<Map<String, TorrentProgress>>(emptyMap())
    val progress: StateFlow<Map<String, TorrentProgress>> = _progress.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    /** Upper-case hex info hash → handle. */
    private val handles = ConcurrentHashMap<String, TorrentHandle>()

    /** When each torrent last made progress, for stall detection. */
    private val lastActivity = ConcurrentHashMap<String, Long>()
    private val stalledReported = ConcurrentHashMap.newKeySet<String>()

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.METADATA_FAILED.swig(),
        )

        override fun alert(alert: Alert<*>) {
            val torrentAlert = alert as? TorrentAlert<*> ?: return
            val hash = runCatching { torrentAlert.handle().infoHash().toHex().uppercase() }.getOrNull()
                ?: return
            when (alert.type()) {
                AlertType.METADATA_RECEIVED -> onMetadata(hash)
                AlertType.METADATA_FAILED -> fail(
                    hash,
                    "Couldn't fetch the torrent's metadata from any peer — this swarm is dead.",
                )
                // The listener only subscribes to the two types above.
                else -> Unit
            }
        }
    }

    /** Starts the libtorrent session. Repeated calls are no-ops. */
    fun start(downloadDir: File) = onEngine {
        if (running) return@onEngine
        runCatching { downloadDir.mkdirs() }

        val settings = SettingsPack()
            .downloadRateLimit(0)
            .uploadRateLimit(0)
            .activeDownloads(3)
            .activeSeeds(2)
            .activeChecking(1)
            .connectionsLimit(MAX_CONNECTIONS)
            .maxPeerlistSize(500)
            .alertQueueSize(2000)
            .stopTrackerTimeout(5)
            .listenInterfaces("0.0.0.0:6881,[::]:6881")
            .apply {
                // DHT + LSD find peers for magnets that carry no trackers.
                setEnableDht(true)
                setEnableLsd(true)
                setMaxMetadataSize(4 * 1024 * 1024)
                setDhtBootstrapNodes(DHT_BOOTSTRAP)
            }

        val session = SessionManager(LOG_ENGINE)
        session.addListener(alertListener)
        session.start(SessionParams(settings))

        manager = session
        running = true
        startTicker()
        Log.i(TAG, "libtorrent ${libtorrentVersion()} started → ${downloadDir.absolutePath}")
    }

    /**
     * Adds a release to the swarm. A magnet is preferred (it is what the API
     * gives us); a `.torrent` payload is used when the caller already has one.
     */
    fun addTorrent(release: TorrentRelease, downloadDir: File) = onEngine {
        val session = manager ?: return@onEngine
        val hash = release.key
        runCatching { downloadDir.mkdirs() }

        // Local vals: `torrentBytes` is mutable and `playableMagnet` is a custom
        // getter, so neither can be smart-cast at the call site.
        val torrentBytes = release.torrentBytes
        val magnet = release.playableMagnet

        val handle: TorrentHandle? = when {
            torrentBytes != null && torrentBytes.isNotEmpty() -> {
                val info = runCatching { TorrentInfo(torrentBytes) }.getOrNull()
                if (info == null) {
                    fail(hash, "That .torrent file could not be decoded.")
                    return@onEngine
                }
                session.download(info, downloadDir, null, null, null, TorrentFlags.AUTO_MANAGED)
                session.find(info.infoHash())
            }
            magnet != null -> {
                session.download(magnet, downloadDir, TorrentFlags.AUTO_MANAGED)
                release.infoHash
                    ?.let { runCatching { org.libtorrent4j.Sha1Hash.parseHex(it) }.getOrNull() }
                    ?.let { session.find(it) }
            }
            else -> {
                fail(hash, "This release has no info hash — add a free API key in Settings.")
                return@onEngine
            }
        }

        if (handle == null) {
            // The handle can appear a moment later; the ticker picks it up by hash.
            Log.w(TAG, "handle for $hash not available yet")
        } else {
            handles[hash] = handle
            runCatching { handle.resume() }
        }
        lastActivity[hash] = System.currentTimeMillis()
        refresh()
    }

    fun pause(infoHash: String) = onEngine {
        handles[infoHash]?.let { runCatching { it.pause() } }
        refresh()
    }

    fun resume(infoHash: String) = onEngine {
        handles[infoHash]?.let { runCatching { it.resume() } }
        refresh()
    }

    fun remove(infoHash: String) = onEngine {
        val handle = handles.remove(infoHash)
        if (handle != null) {
            runCatching { handle.pause() }
            runCatching { manager?.remove(handle) }
        }
        lastActivity.remove(infoHash)
        stalledReported.remove(infoHash)
        _progress.value = _progress.value - infoHash
        _errors.value = _errors.value - infoHash
    }

    /** True while libtorrent is swarming at least one torrent. */
    val isRunning: Boolean
        get() = running

    /**
     * Metadata arrived: prioritise media files only, and record the real size so
     * the UI can show bytes instead of a bare percentage.
     */
    private fun onMetadata(hash: String) {
        val handle = handles[hash]
        val info = handle?.let { runCatching { it.torrentFile() }.getOrNull() }
        if (handle != null && info != null) {
            runCatching {
                val priorities = Priority.array(Priority.SKIP, info.numFiles())
                val storage = info.files()
                for (index in 0 until info.numFiles()) {
                    val path = runCatching { storage.filePath(index) }.getOrNull().orEmpty()
                    if (isWanted(path)) priorities[index] = Priority.DEFAULT
                }
                handle.prioritizeFiles(priorities)
            }
        }
        _progress.value = _progress.value.toMutableMap().apply {
            this[hash]?.let { current ->
                this[hash] = current.copy(
                    hasMetadata = true,
                    totalBytes = info?.totalSize() ?: current.totalBytes,
                    name = info?.let { runCatching { it.name() }.getOrNull() } ?: current.name,
                )
            }
        }
        Log.i(TAG, "metadata received for $hash (${info?.totalSize() ?: 0} bytes)")
        refresh()
    }

    private fun isWanted(path: String): Boolean {
        val lower = path.lowercase()
        if (lower.contains("sample")) return false
        return WANTED_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        executor.scheduleWithFixedDelay(
            { runCatching { refresh() } },
            TICK_MILLIS,
            TICK_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    /** Reads every handle's status; the single writer of [progress]. */
    private fun refresh() {
        if (!running) return
        val session = manager
        val snapshot = LinkedHashMap<String, TorrentProgress>(handles.size + 1)
        val now = System.currentTimeMillis()

        handles.forEach { (hash, handle) ->
            val status: TorrentStatus = runCatching { handle.status() }.getOrNull() ?: return@forEach
            val total = status.totalWanted().takeIf { it > 0 } ?: status.total()
            val done = status.totalWantedDone().takeIf { it > 0 } ?: status.totalDone()
            val rate = status.downloadRate().toLong()
            val finished = status.isFinished() || status.isSeeding()

            if (done > (snapshot[hash]?.downloadedBytes ?: 0L)) lastActivity[hash] = now

            snapshot[hash] = TorrentProgress(
                infoHash = hash,
                state = status.state().name.lowercase(),
                hasMetadata = status.hasMetadata(),
                progress = when {
                    finished -> 1f
                    total > 0 -> (done.toFloat() / total).coerceIn(0f, 1f)
                    else -> status.progress().coerceIn(0f, 1f)
                },
                downloadedBytes = done,
                totalBytes = total,
                downloadRate = rate,
                uploadRate = status.uploadRate().toLong(),
                peers = status.numPeers(),
                seeds = status.numSeeds(),
                etaSeconds = when {
                    rate <= 0 -> -1L
                    else -> ((total - done) / rate).coerceIn(0L, MAX_ETA_SECONDS)
                },
                name = runCatching { status.name() }.getOrNull(),
                isFinished = finished,
            )

            // Stall detection: libtorrent happily sits at 0 peers forever, which
            // looks identical to a hang. Say something useful instead.
            if (!finished && !status.hasMetadata() && status.numPeers() == 0) {
                val idleSince = lastActivity[hash] ?: now
                if (now - idleSince > METADATA_TIMEOUT_MILLIS && stalledReported.add(hash)) {
                    fail(
                        hash,
                        "No peers found for this torrent after 60s. It may be dead, or your " +
                            "network may block BitTorrent traffic.",
                    )
                }
            }
        }

        // A torrent whose handle appeared after addTorrent() (magnet parsing is
        // asynchronous) gets attached here.
        handles.keys.toList().forEach { hash ->
            if (snapshot.containsKey(hash)) return@forEach
            val found = session
                ?.let { runCatching { it.find(org.libtorrent4j.Sha1Hash.parseHex(hash)) }.getOrNull() }
            if (found != null) {
                handles[hash] = found
                runCatching { found.resume() }
            }
        }

        _progress.value = snapshot
    }

    private fun fail(hash: String, message: String) {
        if (_errors.value[hash] == message) return
        _errors.value = _errors.value + (hash to message)
        Log.w(TAG, "torrent $hash: $message")
    }

    fun clearError(infoHash: String) {
        if (_errors.value.containsKey(infoHash)) {
            _errors.value = _errors.value - infoHash
        }
    }

    /** Stops the session; libtorrent's `stop()` blocks, so it runs off-thread. */
    fun stop() = onEngine {
        if (!running) return@onEngine
        runCatching { manager?.removeListener(alertListener) }
        runCatching { manager?.stop() }
        manager = null
        running = false
        handles.clear()
        lastActivity.clear()
        _progress.value = emptyMap()
    }

    fun shutdown() {
        onEngine { runCatching { manager?.stop() } }
        runCatching { executor.shutdown() }
    }

    private fun onEngine(block: () -> Unit) {
        runCatching { executor.execute(block) }
            .onFailure { Log.e(TAG, "engine task rejected", it) }
    }

    private fun libtorrentVersion(): String =
        runCatching { org.libtorrent4j.swig.libtorrent.version() }.getOrDefault("2.x")

    companion object {
        private const val TAG = "TorrentEngine"
        private const val LOG_ENGINE = false
        private const val TICK_MILLIS = 1000L
        private const val MAX_CONNECTIONS = 200
        private const val MAX_ETA_SECONDS = 86_400L * 7
        private const val METADATA_TIMEOUT_MILLIS = 60_000L

        private const val DHT_BOOTSTRAP =
            "router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881," +
                "dht.libtorrent.org:25401,dht.aelitis.com:6881"

        /** Media and subtitle files worth downloading. */
        private val WANTED_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm", ".ts", ".m2ts",
            ".srt", ".ass", ".ssa", ".vtt", ".sub",
        )
    }
}
