package com.torrentclaw.movies.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WatchlistEntity::class,
        CachedMovieEntity::class,
        DownloadEntity::class,
        PlaybackPositionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class TorrentClawDatabase : RoomDatabase() {

    abstract fun watchlistDao(): WatchlistDao

    abstract fun cachedMovieDao(): CachedMovieDao

    abstract fun downloadDao(): DownloadDao

    abstract fun playbackDao(): PlaybackDao

    companion object {
        const val NAME = "torrentclaw_movies.db"

        /**
         * v1 → v2 adds the download queue. Written as an additive migration so
         * existing watchlists and caches survive the update.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `id` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `movie_id` INTEGER NOT NULL,
                        `movie_title` TEXT NOT NULL,
                        `poster_url` TEXT,
                        `quality` TEXT,
                        `source` TEXT,
                        `file_name` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `magnet_uri` TEXT,
                        `info_hash` TEXT,
                        `total_bytes` INTEGER NOT NULL,
                        `downloaded_bytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `local_path` TEXT,
                        `error_message` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `eta_seconds` INTEGER NOT NULL,
                        `bytes_per_second` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_created_at` ON `downloads` (`created_at`)")
            }
        }

        /**
         * v2 → v3 adds resume positions for the in-app player. Additive: existing
         * downloads, watchlist and caches are untouched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playback_positions` (
                        `file_path` TEXT NOT NULL,
                        `movie_id` INTEGER NOT NULL,
                        `movie_title` TEXT NOT NULL,
                        `poster_url` TEXT,
                        `position_ms` INTEGER NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`file_path`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
