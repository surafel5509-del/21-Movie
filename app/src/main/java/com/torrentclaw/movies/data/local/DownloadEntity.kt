package com.torrentclaw.movies.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A download job. Two flavours share one table:
 *
 *  - [Kind.TORRENT_FILE] — the app fetches the `.torrent` file from TorrentClaw
 *    itself (small, fast, resumable) and then hands it to a torrent client.
 *  - [Kind.MEDIA] — a direct HTTP(S) resource (progressive media) streamed to
 *    the app's downloads folder with byte-level progress.
 *
 * Persisting every byte counter means progress survives process death and the
 * UI can resume the same job from Room.
 */
@Entity(
    tableName = "downloads",
    indices = [Index(value = ["status"]), Index(value = ["created_at"])],
)
data class DownloadEntity(
    /** Primary key is the info hash for torrents, or a URL-derived id for media. */
    @PrimaryKey val id: String,
    val kind: String,
    @ColumnInfo(name = "movie_id") val movieId: Int,
    @ColumnInfo(name = "movie_title") val movieTitle: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    val quality: String?,
    val source: String?,
    /** Human-readable file name shown in the UI. */
    @ColumnInfo(name = "file_name") val fileName: String,
    val url: String,
    @ColumnInfo(name = "magnet_uri") val magnetUri: String?,
    @ColumnInfo(name = "info_hash") val infoHash: String?,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long,
    val status: String,
    @ColumnInfo(name = "local_path") val localPath: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Seconds remaining, recomputed by the manager. -1 when unknown. */
    @ColumnInfo(name = "eta_seconds") val etaSeconds: Long = -1,
    /** Bytes/second, smoothed. 0 when idle. */
    @ColumnInfo(name = "bytes_per_second") val bytesPerSecond: Long = 0,
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isActive: Boolean
        get() = status == DownloadStatus.ACTIVE.name ||
            status == DownloadStatus.QUEUED.name ||
            status == DownloadStatus.PAUSED.name

    companion object {
        /** Stable id for a torrent file download. */
        fun torrentId(infoHash: String): String = "torrent:$infoHash"

        /** Stable, filename-safe id for a direct media URL. */
        fun mediaId(url: String): String =
            "media:" + url.hashCode().toUInt().toString(16)
    }
}

/** Lifecycle states; stored as the enum name in [DownloadEntity.status]. */
enum class DownloadStatus {
    QUEUED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED,
    ;

    companion object {
        fun fromStorage(value: String?): DownloadStatus =
            entries.firstOrNull { it.name == value } ?: FAILED
    }
}

enum class DownloadKind {
    TORRENT_FILE,
    MEDIA,
    ;

    companion object {
        fun fromStorage(value: String?): DownloadKind =
            entries.firstOrNull { it.name == value } ?: TORRENT_FILE
    }
}

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','ACTIVE','PAUSED') ORDER BY created_at ASC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','ACTIVE') ORDER BY created_at ASC")
    suspend fun getPending(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE info_hash = :infoHash ORDER BY created_at DESC LIMIT 1")
    suspend fun findByInfoHash(infoHash: String): DownloadEntity?

    @androidx.room.Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query(
        "UPDATE downloads SET downloaded_bytes = :downloaded, total_bytes = :total, " +
            "status = :status, bytes_per_second = :bps, eta_seconds = :eta, updated_at = :now " +
            "WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        downloaded: Long,
        total: Long,
        status: String,
        bps: Long,
        eta: Long,
        now: Long,
    )

    @Query(
        "UPDATE downloads SET status = :status, error_message = :error, " +
            "local_path = :localPath, updated_at = :now WHERE id = :id",
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        error: String?,
        localPath: String?,
        now: Long,
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','CANCELED','FAILED')")
    suspend fun clearFinished()

    @Query("SELECT COALESCE(SUM(total_bytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    fun observeCompletedBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED','ACTIVE','PAUSED')")
    suspend fun activeCount(): Int
}
