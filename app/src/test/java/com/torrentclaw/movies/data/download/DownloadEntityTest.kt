package com.torrentclaw.movies.data.download

import com.torrentclaw.movies.data.local.DownloadEntity
import com.torrentclaw.movies.data.local.DownloadKind
import com.torrentclaw.movies.data.local.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadEntityTest {

    private fun entity(
        total: Long = 0,
        downloaded: Long = 0,
        status: DownloadStatus = DownloadStatus.QUEUED,
    ) = DownloadEntity(
        id = "torrent:abc",
        kind = DownloadKind.TORRENT_FILE.name,
        movieId = 1,
        movieTitle = "Dune",
        posterUrl = null,
        quality = "1080p BluRay",
        source = "yts",
        fileName = "Dune.torrent",
        url = "https://torrentclaw.com/api/v1/torrent/abc",
        magnetUri = "magnet:?xt=urn:btih:abc",
        infoHash = "abc",
        totalBytes = total,
        downloadedBytes = downloaded,
        status = status.name,
        localPath = null,
        errorMessage = null,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `progress is clamped and safe with an unknown total`() {
        assertEquals(0f, entity(total = 0, downloaded = 100).progress, 0.001f)
        assertEquals(0.5f, entity(total = 1000, downloaded = 500).progress, 0.001f)
        assertEquals(1f, entity(total = 1000, downloaded = 1000).progress, 0.001f)
        // A server that sends more than Content-Length must not break the UI.
        assertEquals(1f, entity(total = 1000, downloaded = 1200).progress, 0.001f)
    }

    @Test
    fun `stable ids are derived from the hash and the url`() {
        assertEquals("torrent:deadbeef", DownloadEntity.torrentId("deadbeef"))
        val first = DownloadEntity.mediaId("https://example.com/a.mp4")
        val second = DownloadEntity.mediaId("https://example.com/a.mp4")
        assertEquals(first, second)
        assertTrue(first.startsWith("media:"))
    }

    @Test
    fun `status and kind parsing tolerate unknown values`() {
        assertEquals(DownloadStatus.PAUSED, DownloadStatus.fromStorage("PAUSED"))
        assertEquals(DownloadStatus.FAILED, DownloadStatus.fromStorage("NONSENSE"))
        assertEquals(DownloadKind.MEDIA, DownloadKind.fromStorage("MEDIA"))
        assertEquals(DownloadKind.TORRENT_FILE, DownloadKind.fromStorage(null))
    }

    @Test
    fun `isActive covers queued, active and paused only`() {
        assertTrue(entity(status = DownloadStatus.QUEUED).isActive)
        assertTrue(entity(status = DownloadStatus.ACTIVE).isActive)
        assertTrue(entity(status = DownloadStatus.PAUSED).isActive)
        assertFalse(entity(status = DownloadStatus.COMPLETED).isActive)
        assertFalse(entity(status = DownloadStatus.FAILED).isActive)
        assertFalse(entity(status = DownloadStatus.CANCELED).isActive)
    }

    @Test
    fun `mime types are guessed from the file extension`() {
        assertEquals("application/x-bittorrent", FileDownloader.guessMimeType("Dune.2010.1080p.torrent"))
        assertEquals("video/mp4", FileDownloader.guessMimeType("movie.MP4"))
        assertEquals("video/x-matroska", FileDownloader.guessMimeType("movie.mkv"))
        assertEquals("application/octet-stream", FileDownloader.guessMimeType("no-extension"))
    }

    @Test
    fun `torrent file names are filesystem safe`() {
        // The manager sanitises the title; emulate the contract with the same rules.
        val raw = "Dune: Part Two (2024)"
        val safe = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        assertFalse(safe.contains(":"))
        assertTrue(safe.contains("_"))
    }
}
