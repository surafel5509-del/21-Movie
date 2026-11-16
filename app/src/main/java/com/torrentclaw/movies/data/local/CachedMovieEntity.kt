package com.torrentclaw.movies.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.torrentclaw.movies.domain.model.Movie
import kotlinx.coroutines.flow.Flow

/**
 * Short-lived response cache so the Home screen can render instantly (and
 * offline) while the network refresh runs. TorrentClaw is rate limited to
 * 30 req/min anonymously; serving the first frame from Room keeps refreshes
 * within budget.
 */
@Entity(
    tableName = "cached_movies",
    indices = [Index(value = ["section", "position"])],
)
data class CachedMovieEntity(
    @PrimaryKey val id: Int,
    val section: String,
    val position: Int,
    val title: String,
    @ColumnInfo(name = "original_title") val originalTitle: String?,
    val overview: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "backdrop_url") val backdropUrl: String?,
    val year: Int?,
    @ColumnInfo(name = "rating_imdb") val ratingImdb: Double?,
    @ColumnInfo(name = "rating_tmdb") val ratingTmdb: Double?,
    val genres: String,
    @ColumnInfo(name = "content_url") val contentUrl: String?,
    @ColumnInfo(name = "imdb_id") val imdbId: String?,
    @ColumnInfo(name = "tmdb_id") val tmdbId: String?,
    @ColumnInfo(name = "max_seeders") val maxSeeders: Int,
    @ColumnInfo(name = "best_quality") val bestQuality: String?,
    @ColumnInfo(name = "has_hdr") val hasHdr: Boolean,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
) {
    fun toDomain(): Movie = Movie(
        id = id,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        ratingImdb = ratingImdb,
        ratingTmdb = ratingTmdb,
        genres = genres.split(WatchlistEntity.GENRE_DELIMITER).filter { it.isNotBlank() },
        contentUrl = contentUrl,
        imdbId = imdbId,
        tmdbId = tmdbId,
        maxSeeders = maxSeeders,
        bestQuality = bestQuality,
        hasHdr = hasHdr,
        hasTorrents = true,
    )
}

@Dao
interface CachedMovieDao {

    @Query("SELECT * FROM cached_movies WHERE section = :section ORDER BY position ASC")
    fun observeSection(section: String): Flow<List<CachedMovieEntity>>

    @Query("SELECT * FROM cached_movies WHERE section = :section ORDER BY position ASC")
    suspend fun getSection(section: String): List<CachedMovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CachedMovieEntity>)

    @Query("DELETE FROM cached_movies WHERE section = :section")
    suspend fun clearSection(section: String)

    @Query("DELETE FROM cached_movies WHERE cached_at < :cutoff")
    suspend fun evictOlderThan(cutoff: Long)

    @Query("DELETE FROM cached_movies")
    suspend fun clear()
}
