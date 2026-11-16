package com.torrentclaw.movies.network.dto

import com.torrentclaw.movies.network.MagnetResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the bug that made every movie show "No torrents indexed
 * yet": TorrentClaw's anonymous tier returns releases with `infoHash`,
 * `magnetUrl` and `torrentUrl` omitted, and the mapper used to drop those rows.
 *
 * The JSON below is a trimmed copy of a real `/api/v1/search` response for
 * "The Odyssey" (2026) captured without an API key.
 */
class ReleaseMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val anonymousPayload = """
        {
          "total": 1161, "page": 1, "pageSize": 2,
          "_notice": "Magnet links, info hashes, and torrent downloads require a free API key.",
          "results": [{
            "id": 73263, "imdbId": "tt33764258", "tmdbId": 1368337,
            "contentType": "movie", "title": "The Odyssey", "year": 2026,
            "overview": "Odysseus sails home.", "ratingImdb": "8.0", "ratingTmdb": "8.0",
            "genres": ["Adventure", "Action", "Fantasy"],
            "contentUrl": "/movies/the-odyssey-2026-73263",
            "hasTorrents": true, "originCountry": ["GB", "US"],
            "torrents": [
              {"rawTitle": "The.Odyssey.2026.2160p.BluRay.x265-PSA", "quality": "2160p",
               "codec": "hevc", "sourceType": "bluray", "sizeBytes": 5444395466,
               "seeders": 453, "leechers": 1357, "source": "bitmagnet",
               "qualityScore": 92, "uploadedAt": "2026-11-08T20:56:16.364Z",
               "hdrType": "HDR10", "releaseGroup": "PSA", "languages": ["en"]},
              {"rawTitle": "The Odyssey (2026) 2160p bluray", "quality": "2160p",
               "codec": "hevc", "sourceType": "bluray", "sizeBytes": 8396661064,
               "seeders": 3118, "leechers": 1061, "source": "yts", "qualityScore": 91}
            ]
          }]
        }
    """.trimIndent()

    @Test
    fun `anonymous releases are kept instead of dropped`() {
        val response = json.decodeFromString<SearchResponseDto>(anonymousPayload)
        val detail = response.results.first().toMovieDetail(MagnetResolver())

        // The old code returned 0 here, which is exactly what the screenshot showed.
        assertEquals(2, detail.releases.size)
        assertNotNull(response.notice)
    }

    @Test
    fun `anonymous releases are not downloadable but still carry metadata`() {
        val response = json.decodeFromString<SearchResponseDto>(anonymousPayload)
        val releases = response.results.first().toMovieDetail(MagnetResolver()).releases

        val best = releases.maxByOrNull { it.seeders }!!
        assertEquals(3118, best.seeders)
        assertEquals("2160p", best.quality)
        assertEquals(8_396_661_064L, best.sizeBytes)
        assertFalse(best.isDownloadable)
        assertNull(best.playableMagnet)
        assertNull(best.infoHash)
        // Every release still needs a stable identity for LazyColumn keys.
        assertEquals(releases.size, releases.map { it.key }.distinct().size)
    }

    @Test
    fun `authenticated releases build a magnet and a torrent url`() {
        val payload = anonymousPayload.replace(
            """"seeders": 453, "leechers": 1357, "source": "bitmagnet",""",
            """"infoHash": "DD8255ECDC7CA55FB0BBF81323D87062DB1F6D1C",
               "magnetUrl": "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
               "seeders": 453, "leechers": 1357, "source": "bitmagnet",""",
        )
        val response = json.decodeFromString<SearchResponseDto>(payload)
        val authenticated = response.results.first().toMovieDetail(MagnetResolver()).releases
            .first { it.infoHash != null }

        assertTrue(authenticated.isDownloadable)
        assertEquals("magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c", authenticated.magnetUri)
        // No torrentUrl in the payload: the documented API route is synthesised.
        // The mapper synthesises the documented route from the hash as sent.
        assertTrue(authenticated.torrentUrl!!.startsWith("https://torrentclaw.com/api/v1/torrent/"))
        assertTrue(authenticated.torrentUrl!!.endsWith("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c"))
    }

    @Test
    fun `hash-only releases get a locally built magnet with trackers`() {
        val payload = anonymousPayload.replace(
            """"seeders": 453, "leechers": 1357, "source": "bitmagnet",""",
            """"infoHash": "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
               "seeders": 453, "leechers": 1357, "source": "bitmagnet",""",
        )
        val response = json.decodeFromString<SearchResponseDto>(payload)
        val release = response.results.first().toMovieDetail(MagnetResolver()).releases
            .first { it.infoHash != null }

        val magnet = release.playableMagnet
        assertNotNull(magnet)
        assertTrue(magnet!!.startsWith("magnet:?xt=urn:btih:dd8255ec"))
        assertTrue(magnet.contains("&tr="))
        assertTrue(magnet.contains("tracker.opentrackr.org"))
    }

    @Test
    fun `sizeBytes tolerates numeric and string forms`() {
        val numeric = json.decodeFromString<TorrentDto>("""{"sizeBytes": 5444395466}""")
        val stringy = json.decodeFromString<TorrentDto>("""{"sizeBytes": "5444395466"}""")

        assertEquals(5_444_395_466L, numeric.sizeBytes.toSizeBytes())
        assertEquals(5_444_395_466L, stringy.sizeBytes.toSizeBytes())
        assertNull(json.decodeFromString<TorrentDto>("""{"infoHash":"abc"}""").sizeBytes.toSizeBytes())
    }

    @Test
    fun `unknown fields from newer API versions are ignored`() {
        val future = json.decodeFromString<SearchResultDto>(
            """{"id": 1, "title": "X", "brandNewField": {"nested": true}, "torrents": []}""",
        )
        assertEquals(1, future.id)
        assertTrue(future.torrents.isEmpty())
    }
}
