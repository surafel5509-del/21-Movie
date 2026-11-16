package com.torrentclaw.movies.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WatchlistEntity::class, CachedMovieEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TorrentClawDatabase : RoomDatabase() {

    abstract fun watchlistDao(): WatchlistDao

    abstract fun cachedMovieDao(): CachedMovieDao

    companion object {
        const val NAME = "torrentclaw_movies.db"
    }
}
