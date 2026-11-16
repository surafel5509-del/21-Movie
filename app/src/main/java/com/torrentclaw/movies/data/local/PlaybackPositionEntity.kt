package com.torrentclaw.movies.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Where the user stopped watching a file.
 *
 * Keyed by the *file path* rather than the movie id: the same movie can exist as
 * a 1080p and a 2160p download, and resuming the wrong one at the wrong offset
 * is worse than not resuming at all.
 *
 * The player writes this on every position change (throttled) and on pause, so a
 * process death, a swipe from recents or an "open in another app" round trip all
 * come back to the last watched second.
 */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "movie_id") val movieId: Int,
    @ColumnInfo(name = "movie_title") val movieTitle: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    /** Milliseconds into the file. */
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    /** Total duration, 0 while still unknown. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    /** 0..1 watched fraction, or null when the duration is not known yet. */
    val watchedFraction: Float?
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
}

@Dao
interface PlaybackDao {

    /** Resume list for the Downloads tab, most recent first. */
    @Query("SELECT * FROM playback_positions ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>

    /**
     * Watch history, excluding bare taps: anything under a minute in or with less
     * than 2 % watched would otherwise clutter the list.
     */
    @Query(
        "SELECT * FROM playback_positions " +
            "WHERE position_ms > 60000 AND (duration_ms = 0 OR position_ms < duration_ms * 0.98) " +
            "ORDER BY updated_at DESC LIMIT :limit",
    )
    fun observeContinueWatching(limit: Int = 20): Flow<List<PlaybackPositionEntity>>

    @Query("SELECT * FROM playback_positions WHERE file_path = :filePath LIMIT 1")
    suspend fun findByPath(filePath: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions WHERE file_path = :filePath LIMIT 1")
    fun observeByPath(filePath: String): Flow<PlaybackPositionEntity?>

    @Upsert
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE file_path = :filePath")
    suspend fun deleteByPath(filePath: String)
}
