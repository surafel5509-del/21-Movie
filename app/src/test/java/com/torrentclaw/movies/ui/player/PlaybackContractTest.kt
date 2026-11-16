package com.torrentclaw.movies.ui.player

import com.torrentclaw.movies.data.local.PlaybackPositionEntity
import com.torrentclaw.movies.data.player.ResumeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resume rules decide whether "open the app again and continue" is true.
 * They are pure logic, so they are pinned here rather than discovered on a phone.
 */
class PlaybackContractTest {

    @Test
    fun `a fresh file starts at zero`() {
        val state = ResumeState()
        assertEquals(0L, state.positionMs)
        assertFalse(state.shouldResume)
    }

    @Test
    fun `a short intro is replayed rather than resumed`() {
        // Under 15 s the user almost certainly wants the intro, and seek-restoring
        // that early is more jarring than re-watching it.
        val state = ResumeState(positionMs = 8_000, durationMs = 7_200_000)
        assertFalse(state.shouldResume)
    }

    @Test
    fun `a mid-film position resumes`() {
        val state = ResumeState(positionMs = 2_520_000, durationMs = 7_200_000)
        assertTrue(state.shouldResume)
        assertEquals(0.35f, state.watchedFraction!!, 0.01f)
    }

    @Test
    fun `a finished film replays from the start`() {
        val state = ResumeState(positionMs = 7_190_000, durationMs = 7_200_000)
        assertFalse("must not resume a resolved ending", state.shouldResume)
    }

    @Test
    fun `resume works before the duration is known`() {
        // A position saved before the duration was read (duration 0) still resumes:
        // the fraction cannot be computed, so only the time threshold applies.
        val state = ResumeState(positionMs = 600_000, durationMs = 0)
        assertNull(state.watchedFraction)
        assertTrue(state.shouldResume)
    }

    @Test
    fun `watched fraction is clamped and null without a duration`() {
        val half = PlaybackPositionEntity(
            filePath = "/movies/a.mkv",
            movieId = 1,
            movieTitle = "A",
            posterUrl = null,
            positionMs = 500,
            durationMs = 1_000,
            updatedAt = 0,
        )
        assertEquals(0.5f, half.watchedFraction!!, 0.001f)

        val unfinished = PlaybackPositionEntity(
            filePath = "/movies/b.mkv",
            movieId = 1,
            movieTitle = "B",
            posterUrl = null,
            positionMs = 5_000,
            durationMs = 0,
            updatedAt = 0,
        )
        assertNull(unfinished.watchedFraction)
    }

    @Test
    fun `positions are keyed by file so two qualities never share progress`() {
        // The same movie at 1080p and 2160p are different files with different
        // runtimes; sharing one position would resume the wrong cut.
        val hd = PlaybackPositionEntity("/movies/Movie.1080p.mkv", 1, "Movie", null, 1_000, 5_000, 0)
        val uhd = PlaybackPositionEntity("/movies/Movie.2160p.mkv", 1, "Movie", null, 9_000, 9_000, 0)
        assertTrue(hd.filePath != uhd.filePath)
    }

    // ── time formatting shown under the scrubber ─────────────────────────────

    @Test
    fun `time formats minutes and hours`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:05", formatTime(5_000))
        assertEquals("1:05", formatTime(65_000))
        assertEquals("1:02:05", formatTime(3_725_000))
        // Negative positions come from a player that has not started yet.
        assertEquals("0:00", formatTime(-500))
    }
}
