package com.torrentclaw.movies.network.dto

import com.torrentclaw.movies.network.MagnetResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val resolver = MagnetResolver()

    private val searchJson = """
    {
      "total": 42, "page": 1, "pageSize": 20,
      "results": [
        {
          "id": 1,
          "imdbId": "tt1375666",
          "tmdbId": "27205",
          "contentType": "movie",
          "title": "Inception",
          "year": 2010,
          "overview": "A thief who steals corporate secrets.",
          "posterUrl": "https://image.tmdb.org/t/p/w500/poster.jpg",
          "backdropUrl": "https://image.tmdb.org/t/p/w780/backdrop.jpg",
          "genres": ["Action", "Science Fiction"],
          "ratingImdb": "8.8",
          "ratingTmdb": "8.4",
          "contentUrl": "/movies/inception-2010-1",
          "hasTorrents": true,
          "maxSeeders": 847,
          "torrents": [
            {
              "infoHash": "aaf1e71c0a0e3b1c0f1a2b3c4d5e6f7a8b9c0d1e",
              "magnetUrl": "magnet:?xt=urn:btih:aaf1e71c&dn=Inception",
              "quality": "1080p",
              "codec": "x265",
              "sourceType": "BluRay",
              "sizeBytes": "2147483648",
              "seeders": 847,
              "leechers": 23,
              "source": "yts",
              "qualityScore": 85,
              "languages": ["en"]
            },
            {
              "infoHash": "baf1e71c0a0e3b1c0f1a2b3c4d5e6f7a8b9c0d1e",
              "magnetUrl": null,
              "quality": "2160p",
              "hdrType": "dolby_vision",
              "sizeBytes": "8589934592",
              "seeders": 120,
              "leechers": 4,
              "source": "yts"
            }
          ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `parses the documented search payload`() {
        val response = json.decodeFromString<SearchResponseDto>(searchJson)
        assertEquals(1, response.results.size)
        val dto = response.results.first()
        assertEquals("Inception", dto.title)
        assertEquals(2, dto.torrents.size)
        assertEquals("2147483648", dto.torrents.first().sizeBytes)
    }

    @Test
    fun `maps movie metadata including stringified ratings`() {
        val movie = json.decodeFromString<SearchResponseDto>(searchJson).results.first().toMovie()
        assertEquals(1, movie.id)
        assertEquals("Inception", movie.title)
        assertEquals(2010, movie.year)
        assertEquals(8.8, movie.ratingImdb!!, 0.001)
        assertEquals("8.8", movie.ratingLabel)
        assertEquals(listOf("Action", "Science Fiction"), movie.genres)
        assertEquals(847, movie.maxSeeders)
        // Highest resolution wins the badge.
        assertEquals("4K", movie.bestQuality)
        assertTrue(movie.hasHdr)
    }

    @Test
    fun `detail mapping sorts releases by seeders and rebuilds missing magnets`() {
        val detail = json.decodeFromString<SearchResponseDto>(searchJson)
            .results.first()
            .toMovieDetail(resolver)

        assertEquals(2, detail.releases.size)
        assertEquals(847, detail.releases[0].seeders)
        assertEquals(120, detail.releases[1].seeders)
        assertEquals(2_147_483_648L, detail.releases[0].sizeBytes)
        assertEquals("1080p BluRay", detail.releases[0].qualityLabel)
        assertEquals("4K HDR", detail.releases[1].qualityLabel)

        // The second release had no magnetUrl: it must be rebuilt from the hash.
        val rebuilt = detail.releases[1].magnetUri
        assertNotNull(rebuilt)
        assertTrue(rebuilt!!.startsWith("magnet:?xt=urn:btih:"))
        assertTrue(rebuilt.contains("tracker.opentrackr.org"))
    }

    /**
     * This used to assert the opposite ("releases without hash or magnet are
     * dropped"), which is the exact behaviour that made every movie show
     * "No torrents indexed yet" on the anonymous tier. They must be kept.
     */
    @Test
    fun `releases without hash or magnet are kept but not downloadable`() {
        val dto = json.decodeFromString<SearchResponseDto>(searchJson).results.first()
        val base = dto.torrents.first().copy(infoHash = "", magnetUrl = null)
        val anonymous = dto.copy(
            // Two *distinct* anonymous releases (different size/seeders); byte-
            // identical duplicates are legitimately collapsed by the mapper.
            torrents = listOf(
                base,
                base.copy(magnetUrl = "not-a-magnet", seeders = 42, sizeBytes = "2048"),
            ),
        )
        val detail = anonymous.toMovieDetail(resolver)

        assertEquals(2, detail.releases.size)
        detail.releases.forEach { release ->
            assertNull(release.infoHash)
            assertNull(release.playableMagnet)
            assertFalse(release.isDownloadable)
        }
        // Distinct keys, or the LazyColumn would crash on duplicate null keys.
        assertEquals(2, detail.releases.map { it.key }.distinct().size)
    }

    @Test
    fun `magnet resolver prefers the server magnet and falls back to a rebuild`() {
        val server = resolver.resolve(" magnet:?xt=urn:btih:abc ", "abc", "Title")
        assertEquals("magnet:?xt=urn:btih:abc", server)

        val rebuilt = resolver.resolve(null, "abc123", "The Title")
        assertNotNull(rebuilt)
        assertTrue(rebuilt!!.contains("dn=The%20Title"))

        assertEquals(null, resolver.resolve(null, "", null))
        assertFalse(resolver.isMagnet("http://example.com/x.torrent"))
        assertTrue(resolver.isMagnet(rebuilt))
    }

    @Test
    fun `size and rating parsers tolerate garbage`() {
        assertEquals(null, "not a number".toSizeBytes())
        assertEquals(null, "0".toSizeBytes())
        assertEquals(1024L, "1024".toSizeBytes())
        assertEquals(null, "--".toRating())
        assertEquals(null, "11.5".toRating())
        assertEquals(7.25, "7,25".toRating()!!, 0.001)
    }

    @Test
    fun `sortedFor honours the requested order`() {
        val detail = json.decodeFromString<SearchResponseDto>(searchJson)
            .results.first()
            .toMovieDetail(resolver)
        assertEquals(847, detail.releases.sortedFor(com.torrentclaw.movies.domain.model.SortOption.SEEDERS).first().seeders)
        assertEquals(
            8_589_934_592L,
            detail.releases.sortedFor(com.torrentclaw.movies.domain.model.SortOption.SIZE).first().sizeBytes,
        )
    }
}
