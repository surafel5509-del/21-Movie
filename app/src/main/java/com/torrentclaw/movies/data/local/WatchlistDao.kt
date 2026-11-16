package com.torrentclaw.movies.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY saved_at DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :movieId)")
    fun observeIsBookmarked(movieId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :movieId)")
    suspend fun isBookmarked(movieId: Int): Boolean

    @Query("SELECT * FROM watchlist WHERE id = :movieId LIMIT 1")
    suspend fun findById(movieId: Int): WatchlistEntity?

    @Query("SELECT * FROM watchlist ORDER BY saved_at DESC")
    suspend fun getAll(): List<WatchlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :movieId")
    suspend fun deleteById(movieId: Int)

    @Query("SELECT COUNT(*) FROM watchlist")
    suspend fun count(): Int

    @Query("DELETE FROM watchlist")
    suspend fun clear()
}
