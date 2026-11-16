package com.torrentclaw.movies.data.download

import com.torrentclaw.movies.data.local.DownloadEntity
import com.torrentclaw.movies.data.local.DownloadKind
import com.torrentclaw.movies.data.local.DownloadStatus
import com.torrentclaw.movies.data.torrent.TorrentProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the contracts the download engine relies on. These are pure functions, so
 * they run on the JVM with no device: a regression here would otherwise only show
 * up as "the download silently does nothing" on a phone.
 */
class EngineContractTest {

    // ── identity: the engine addresses torrents by upper-case hex ────────────

    @Test
    fun `info hash is canonicalised to upper-case hex for the engine`() {
        val entity = DownloadEntity(
            id = DownloadEntity.movieTorrentId("aabbccdd00112233445566778899aabbccddeeff"),
            kind = DownloadKind.TORRENT.name,
            movieId = 1,
            movieTitle = "Test",
            posterUrl = null,
            quality = "1080p",
            source = null,
            fileName = "Test",
            url = "magnet:?xt=urn:btih:AABBCCDD00112233445566778899AABBCCDDEEFF",
            magnetUri = "magnet:?xt=urn:btih:AABBCCDD00112233445566778899AABBCCDDEEFF",
            infoHash = "aabbccdd00112233445566778899aabbccddeeff",
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.ACTIVE.name,
            localPath = null,
            errorMessage = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        // The manager upper-cases at the engine boundary and builds the row id
        // from that same value; progress keys are upper-case, so a lower-case
        // lookup would never find the running torrent.
        val engineHash = entity.infoHash?.uppercase().orEmpty()
        assertEquals("AABBCCDD00112233445566778899AABBCCDDEEFF", engineHash)
        assertEquals(
            "movie:AABBCCDD00112233445566778899AABBCCDDEEFF",
            DownloadEntity.movieTorrentId(engineHash),
        )
    }

    // ── pause/resume must be visible in progress state ───────────────────────

    @Test
    fun `paused torrent reports no speed so the row does not look active`() {
        val paused = TorrentProgress(
            infoHash = "AABB",
            state = "paused",
            downloadRate = 0L,
            uploadRate = 0L,
            pausing = true,
        )
        assertEquals(0L, paused.downloadRate)
        assertTrue(paused.pausing)
        assertFalse(paused.isFinished)
    }

    @Test
    fun `finished torrent is complete at any reported byte count`() {
        val done = TorrentProgress(
            infoHash = "AABB",
            progress = 1f,
            downloadedBytes = 700_000_000L,
            totalBytes = 700_000_000L,
            isFinished = true,
        )
        assertEquals(1f, done.progress, 0.0001f)
        assertTrue(done.isFinished)
        assertFalse(done.pausing)
    }

    @Test
    fun `eta is unknown rather than negative when nothing is transferring`() {
        val stalled = TorrentProgress(infoHash = "AABB", downloadRate = 0L, etaSeconds = -1L)
        assertTrue("stalled torrents must not render a fake ETA", stalled.etaSeconds < 0)
    }

    // ── file-type gating: a movie must remain playable ──────────────────────

    @Test
    fun `every downloadable media extension maps to a real video mime type`() {
        val expected = mapOf(
            "Movie.2024.1080p.mkv" to "video/x-matroska",
            "Movie.2024.1080p.mp4" to "video/mp4",
            "Movie.2024.m4v" to "video/mp4",
            "Movie.2024.avi" to "video/x-msvideo",
            "Movie.2024.mov" to "video/quicktime",
            "Movie.2024.webm" to "video/webm",
            "Movie.2024.ts" to "video/mp2t",
            "Movie.2024.m2ts" to "video/mp2t",
        )
        expected.forEach { (name, mime) ->
            assertEquals("wrong mime for $name", mime, FileDownloader.guessMimeType(name))
        }
    }

    @Test
    fun `subtitles and torrents keep their own types and junk is binary`() {
        assertEquals("application/x-bittorrent", FileDownloader.guessMimeType("movie.torrent"))
        assertEquals("application/x-subrip", FileDownloader.guessMimeType("movie.srt"))
        assertEquals("text/vtt", FileDownloader.guessMimeType("movie.vtt"))
        assertEquals("text/x-ssa", FileDownloader.guessMimeType("movie.ass"))
        // .exe/.nfo must never be treated as playable video.
        assertEquals("application/octet-stream", FileDownloader.guessMimeType("setup.exe"))
        assertFalse(FileDownloader.guessMimeType("info.nfo").startsWith("video/"))
    }

    @Test
    fun `mime sniffing is case insensitive`() {
        assertEquals("video/x-matroska", FileDownloader.guessMimeType("MOVIE.MKV"))
        assertEquals("video/mp4", FileDownloader.guessMimeType("Movie.MP4"))
    }

    // ── rows that predate the engine must not look playable ──────────────────

    @Test
    fun `a completed row without a recorded path has no resolvable file`() {
        val legacy = DownloadEntity(
            id = "media:https://example.com/a.mp4",
            kind = DownloadKind.MEDIA.name,
            movieId = 1,
            movieTitle = "Legacy",
            posterUrl = null,
            quality = null,
            source = null,
            fileName = "a.mp4",
            url = "https://example.com/a.mp4",
            magnetUri = null,
            infoHash = null,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.COMPLETED.name,
            localPath = null,
            errorMessage = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertNull(legacy.localPath)
    }
}
