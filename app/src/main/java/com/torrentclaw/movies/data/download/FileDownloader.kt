package com.torrentclaw.movies.data.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/** Where a completed file ended up. */
data class DownloadOutcome(
    val filePath: String,
    val totalBytes: Long,
    val mimeType: String,
)

/** Progress ticks emitted while streaming (throttled to ~4 per second). */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
)

/**
 * Streams an HTTP(S) resource to disk with **resume support**.
 *
 * Design notes:
 *  - Bytes go to `File` (app-private cache dir) first and are only published to
 *    MediaStore/Downloads when the transfer completes, so a partial file never
 *    appears as a real download.
 *  - `Range: bytes=<offset>-` resumes an interrupted transfer; the server's
 *    `206 Partial Content` is required for the append path, otherwise the file
 *    restarts from zero (some CDNs ignore Range).
 *  - Every read loop checks coroutine cancellation, so cancelling the job stops
 *    network I/O immediately instead of waiting for the buffer to drain.
 *  - Progress callbacks are rate limited: Room writes must not happen per-chunk.
 */
@Singleton
class FileDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {

    private val stagingDir: File
        get() = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }

    /**
     * Downloads [url] into a staging file and returns it. [onProgress] is
     * invoked at most every [PROGRESS_INTERVAL_MS].
     *
     * Throws [IOException] on network failures and [CancellationException] when
     * the coroutine is cancelled — callers translate those into download states.
     */
    suspend fun download(
        url: String,
        targetFileName: String,
        existingBytes: Long = 0L,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(targetFileName)
        val stagingFile = File(stagingDir, safeName)

        var offset = existingBytes.coerceAtLeast(0L)
        if (offset > 0 && !stagingFile.exists()) offset = 0L
        if (offset > 0 && stagingFile.length() != offset) offset = 0L

        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val partial = response.code == 206
            if (offset > 0 && !partial) {
                // Server ignored the Range header: start over cleanly.
                offset = 0L
                stagingFile.delete()
            }

            val contentLength = body.contentLength()
            val total = if (contentLength > 0) offset + contentLength else 0L

            RandomAccessFile(stagingFile, "rw").use { file ->
                file.seek(offset)
                var downloaded = offset
                var windowStart = System.currentTimeMillis()
                var windowBytes = 0L
                var lastReport = 0L
                var smoothedBps = 0L

                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        file.write(buffer, 0, read)
                        downloaded += read
                        windowBytes += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - windowStart
                        if (elapsed >= PROGRESS_INTERVAL_MS) {
                            val instant = (windowBytes * 1000L) / elapsed.coerceAtLeast(1)
                            // Exponential smoothing keeps the ETA from jittering.
                            smoothedBps = if (smoothedBps == 0L) instant else (smoothedBps * 3 + instant) / 4
                            val remaining = if (total > 0 && smoothedBps > 0) {
                                ((total - downloaded) / smoothedBps).coerceAtLeast(0)
                            } else {
                                -1L
                            }
                            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                                onProgress(DownloadProgress(downloaded, total, smoothedBps, remaining))
                                lastReport = now
                            }
                            windowStart = now
                            windowBytes = 0
                        }
                    }
                }

                file.fd.sync()
                onProgress(DownloadProgress(downloaded, total.takeIf { it > 0 } ?: downloaded, 0, 0))
            }
        }

        DownloadOutcome(
            filePath = stagingFile.absolutePath,
            totalBytes = stagingFile.length(),
            mimeType = guessMimeType(safeName),
        )
    }

    /**
     * Publishes a finished staging file to the public Downloads collection
     * (API 29+) or the legacy public directory, then removes the staging copy.
     * Returns the human-readable location.
     */
    suspend fun publishToDownloads(stagingPath: String, displayName: String): String =
        withContext(Dispatchers.IO) {
            val source = File(stagingPath)
            if (!source.exists()) throw IOException("Downloaded file is missing")

            val mime = guessMimeType(displayName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TorrentClaw")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values)
                    ?: throw IOException("Couldn't reserve a Downloads entry")
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out, BUFFER_SIZE) }
                } ?: throw IOException("Couldn't open the Downloads entry for writing")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                source.delete()
                "Downloads/TorrentClaw/$displayName"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "TorrentClaw",
                ).apply { mkdirs() }
                val target = File(dir, displayName)
                source.copyTo(target, overwrite = true)
                source.delete()
                target.absolutePath
            }
        }

    /** Deletes a staging file (used when the user cancels). */
    suspend fun discard(stagingPath: String?) = withContext(Dispatchers.IO) {
        if (!stagingPath.isNullOrBlank()) runCatching { File(stagingPath).delete() }
    }

    fun stagingFileFor(fileName: String): File = File(stagingDir, sanitizeFileName(fileName))

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trimStart('.')
            .take(120)
        return cleaned.ifBlank { "download_${System.currentTimeMillis()}" }
    }

    companion object {
        private const val STAGING_DIR = "downloads_staging"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 250L

        /**
         * Every extension in `TorrentEngine.WANTED_EXTENSIONS` must map to a real
         * type here: an unknown extension becomes `application/octet-stream`, which
         * no video player will open.
         */
        fun guessMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
            "torrent" -> "application/x-bittorrent"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "ts", "m2ts" -> "video/mp2t"
            "srt" -> "application/x-subrip"
            "ass", "ssa" -> "text/x-ssa"
            "vtt" -> "text/vtt"
            "sub" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
