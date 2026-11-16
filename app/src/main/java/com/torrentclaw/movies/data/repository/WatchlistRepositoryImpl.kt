package com.torrentclaw.movies.data.repository

import com.torrentclaw.movies.data.local.WatchlistDao
import com.torrentclaw.movies.data.local.WatchlistEntity
import com.torrentclaw.movies.domain.model.Movie
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val dao: WatchlistDao,
    private val ioDispatcher: CoroutineDispatcher,
) : WatchlistRepository {

    override fun observeWatchlist(): Flow<List<Movie>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .catch { emit(emptyList()) }
        .flowOn(ioDispatcher)

    override fun observeIsBookmarked(movieId: Int): Flow<Boolean> =
        dao.observeIsBookmarked(movieId)
            .catch { emit(false) }
            .flowOn(ioDispatcher)

    override suspend fun isBookmarked(movieId: Int): Boolean = withContext(ioDispatcher) {
        dao.isBookmarked(movieId)
    }

    override suspend fun toggle(movie: Movie): Boolean = withContext(ioDispatcher) {
        val saved = dao.isBookmarked(movie.id)
        if (saved) {
            dao.deleteById(movie.id)
            false
        } else {
            dao.upsert(WatchlistEntity.from(movie, savedAt = System.currentTimeMillis()))
            true
        }
    }

    override suspend fun remove(movieId: Int) = withContext(ioDispatcher) {
        dao.deleteById(movieId)
    }

    override suspend fun restore(movie: Movie) = withContext(ioDispatcher) {
        // Keep the original "saved at" ordering intent by stamping a fresh timestamp.
        dao.upsert(WatchlistEntity.from(movie, savedAt = System.currentTimeMillis()))
    }

    override suspend fun count(): Int = withContext(ioDispatcher) { dao.count() }
}
