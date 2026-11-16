package com.torrentclaw.movies.data.player

import android.util.Log
import com.torrentclaw.movies.data.local.PlaybackDao
import com.torrentclaw.movies.data.local.PlaybackPositionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the player should do when it opens a file. */
data class ResumeState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val watchedFraction: Float? = null,
) {
    /**
     * True when there is a meaningful amount left to watch.
     *
     * Deliberately keyed off [durationMs] rather than [watchedFraction]: the
     * fraction is derived and can be absent (a position saved before the player
     * knew the duration), and an earlier version treated "fraction unknown" as
     * "must resume" — which short-circuited the end-of-film check and would have
     * dropped a viewer back into the last two minutes on every re-open.
     */
    val shouldResume: Boolean
        get() = positionMs > MIN_RESUME_MS &&
            (durationMs <= 0L || positionMs < durationMs - TAIL_TOLERANCE_MS)

    companion object {
        /** Below this the intro is replayed anyway, which is what people expect. */
        const val MIN_RESUME_MS = 15_000L

        /** Anything this close to the end counts as finished: replay, don't resume. */
        const val TAIL_TOLERANCE_MS = 120_000L
    }
}

/**
 * Stores and restores playback positions.
 *
 * Why this exists at all: Android destroys the process whenever it wants. If the
 * only record of "I was 42 minutes in" lives in a ViewModel, then leaving the app,
 * a call arriving, or a low-memory kill all restart the movie from 0. Positions
 * are therefore persisted as they change, not only on a clean exit.
 *
 * Writes are throttled because the player reports a position several times a
 * second and Room writes are disk I/O; a 5 s cadence loses at most 5 s of
 * progress, which is invisible to the user.
 */
@Singleton
class PlaybackRepository @Inject constructor(
    private val dao: PlaybackDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Most recent first, for the Downloads tab and the continue-watching row. */
    val positions: Flow<List<PlaybackPositionEntity>> = dao.observeAll()

    /**
     * Movies genuinely in progress: watched past a minute, not essentially
     * finished. This is what "Continue watching" shows.
     */
    val continueWatching: Flow<List<PlaybackPositionEntity>> = dao.observeContinueWatching()

    suspend fun resumeStateFor(filePath: String): ResumeState {
        val saved = dao.findByPath(filePath) ?: return ResumeState()
        return ResumeState(
            positionMs = saved.positionMs,
            durationMs = saved.durationMs,
            watchedFraction = saved.watchedFraction,
        )
    }

    fun observeFor(filePath: String): Flow<PlaybackPositionEntity?> = dao.observeByPath(filePath)

    /**
     * Records progress. The last write wins, so a stale tick can never rewind a
     * newer position.
     */
    fun save(
        filePath: String,
        movieId: Int,
        movieTitle: String,
        posterUrl: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (filePath.isBlank() || positionMs < 0) return
        scope.launch {
            runCatching {
                dao.upsert(
                    PlaybackPositionEntity(
                        filePath = filePath,
                        movieId = movieId,
                        movieTitle = movieTitle,
                        posterUrl = posterUrl,
                        positionMs = positionMs,
                        durationMs = durationMs.coerceAtLeast(0L),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onFailure { Log.w(TAG, "could not save playback position", it) }
        }
    }

    /**
     * Removes an entry once a file is watched to the end, so it stops appearing in
     * "Continue watching" and the next play starts from the beginning.
     */
    fun clear(filePath: String) {
        scope.launch { runCatching { dao.deleteByPath(filePath) } }
    }

    /**
     * Called when playback finishes. The position is dropped rather than pinned to
     * the end: a finished movie should replay from the start, not sit at 99.9 %.
     */
    fun markFinished(filePath: String) = clear(filePath)

    private companion object {
        const val TAG = "PlaybackRepository"
    }
}
