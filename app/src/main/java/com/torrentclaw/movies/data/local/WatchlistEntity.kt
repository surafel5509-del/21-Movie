package com.torrentclaw.movies.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.torrentclaw.movies.domain.model.Movie

/**
 * A bookmarked movie. Genres are stored as a delimiter-joined string instead of
 * a type converter: the list is small, read-only and never queried, so a join
 * table would be pure overhead.
 */
@Entity(
    tableName = "watchlist",
    indices = [Index(value = ["title"]), Index(value = ["saved_at"])],
)
data class WatchlistEntity(
    @PrimaryKey val id: Int,
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
    @ColumnInfo(name = "saved_at") val savedAt: Long,
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
        genres = genres.split(GENRE_DELIMITER).filter { it.isNotBlank() },
        contentUrl = contentUrl,
        imdbId = imdbId,
        tmdbId = tmdbId,
        maxSeeders = maxSeeders,
        bestQuality = bestQuality,
        hasHdr = hasHdr,
        hasTorrents = true,
    )

    companion object {
        const val GENRE_DELIMITER = "\u001F"

        fun from(movie: Movie, savedAt: Long): WatchlistEntity = WatchlistEntity(
            id = movie.id,
            title = movie.title,
            originalTitle = movie.originalTitle,
            overview = movie.overview,
            posterUrl = movie.posterUrl,
            backdropUrl = movie.backdropUrl,
            year = movie.year,
            ratingImdb = movie.ratingImdb,
            ratingTmdb = movie.ratingTmdb,
            genres = movie.genres.joinToString(GENRE_DELIMITER),
            contentUrl = movie.contentUrl,
            imdbId = movie.imdbId,
            tmdbId = movie.tmdbId,
            maxSeeders = movie.maxSeeders,
            bestQuality = movie.bestQuality,
            hasHdr = movie.hasHdr,
            savedAt = savedAt,
        )
    }
}
