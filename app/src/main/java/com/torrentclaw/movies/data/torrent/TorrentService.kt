package com.torrentclaw.movies.data.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.torrentclaw.movies.MainActivity
import com.torrentclaw.movies.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the BitTorrent session alive while the app is in the background.
 *
 * Android kills background processes; a swarm that dies every time the user
 * switches apps would never finish a real download. This is a *foreground*
 * service with a `dataSync` type, which is the correct (and permitted)
 * classification for an active transfer — `dataSync` also has a 6-hour budget
 * per launch on Android 15, which is enough for one movie.
 *
 * The service owns nothing: it starts the engine, mirrors its progress into a
 * notification, and stops itself once no torrent is active.
 */
@AndroidEntryPoint
class TorrentService : Service() {

    @Inject
    lateinit var engine: TorrentEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitor: Job? = null
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        android.util.Log.i(TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        startedAt = if (startedAt == 0L) System.currentTimeMillis() else startedAt
        val notification = buildNotification(0, 0L, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        engine.start(downloadDir())
        observeProgress()
        return START_STICKY
    }

    /** Mirrors engine progress into the notification until nothing is running. */
    private fun observeProgress() {
        if (monitor?.isActive == true) return
        monitor = scope.launch {
            engine.progress.collectLatest { snapshot ->
                val active = snapshot.values.filter { !it.isFinished }
                if (active.isEmpty() && snapshot.isNotEmpty()) {
                    notifyFinished(snapshot)
                } else if (snapshot.isEmpty()) {
                    // Nothing queued: let the system reclaim us after a grace period.
                    delay(IDLE_GRACE_MILLIS)
                    if (engine.progress.value.values.none { !it.isFinished }) stopSelf()
                } else {
                    val done = active.sumOf { it.downloadedBytes }
                    val total = active.sumOf { it.totalBytes }
                    val rate = active.sumOf { it.downloadRate }
                    update(buildNotification(active.size, rate, percent(done, total), active.firstOrNull()?.name))
                }
            }
        }
    }

    private fun notifyFinished(snapshot: Map<String, TorrentProgress>) {
        val completed = snapshot.values.count { it.isFinished }
        if (completed > 0) {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_DONE_ID,
                baseNotification()
                    .setContentTitle(getString(R.string.torrent_notification_done_title))
                    .setContentText(
                        resources.getQuantityString(
                            R.plurals.torrent_notification_done_text,
                            completed,
                            completed,
                        ),
                    )
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build(),
            )
        }
        stopSelf()
    }

    private fun update(notification: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun buildNotification(
        activeCount: Int,
        bytesPerSecond: Long,
        percent: Int,
        name: String? = null,
    ): Notification {
        val title = if (activeCount <= 1) {
            getString(R.string.torrent_notification_title_single)
        } else {
            getString(R.string.torrent_notification_title_many, activeCount)
        }
        val text = name ?: getString(R.string.torrent_notification_waiting)
        return baseNotification()
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(
                getString(
                    R.string.torrent_notification_speed,
                    formatSpeed(bytesPerSecond),
                    formatElapsed(System.currentTimeMillis() - startedAt),
                ),
            )
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun baseNotification(): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TorrentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.torrent_notification_stop), stop)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.torrent_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.torrent_notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Movies are written to the app's external files dir (no permission needed). */
    private fun downloadDir() =
        java.io.File(getExternalFilesDir(null) ?: filesDir, "Movies").apply { mkdirs() }

    override fun onDestroy() {
        monitor?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TorrentService"
        private const val CHANNEL_ID = "torrent_downloads"
        private const val NOTIFICATION_ID = 7011
        private const val NOTIFICATION_DONE_ID = 7012
        private const val IDLE_GRACE_MILLIS = 5_000L
        const val ACTION_STOP = "com.torrentclaw.movies.action.STOP_TORRENTS"

        @JvmStatic
        fun start(context: Context) {
            context.startService(Intent(context, TorrentService::class.java))
        }

        @JvmStatic
        fun stop(context: Context) {
            context.startService(
                Intent(context, TorrentService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

internal fun percent(done: Long, total: Long): Int =
    if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)

internal fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0 -> "—"
    bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
    bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
    else -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
}

internal fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
