package com.torrentclaw.movies.network.dto

import com.torrentclaw.movies.network.MagnetResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the *complete* TorrentClaw search response for "The Odyssey" (2026),
 * captured live, exactly the way the app does.
 *
 * Why this exists: the detail screen showed "No torrents indexed yet" for this
 * movie while the API returned 10+ releases. Every earlier test used a trimmed
 * payload, which is precisely the kind of fixture that hides a decoding problem.
 * This one keeps every field the API actually sends — including ones the app
 * model does not declare (`audioTracks`, `subtitleTracks`, `torrentFiles`,
 * `videoInfo`, `hdrType`, `subtitleLanguages`, `matchedAlt`, ...) and values that
 * look hostile (`uploadedAt: null`, `"hdr": ""`, `bitDepth: 0`, numbers-as-strings).
 */
class RealPayloadDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val magnetResolver = MagnetResolver()

    /** Verbatim from the live API (anonymous tier, `limit=5`). */
    private val livePayload = """
    {
      "total": 1022, "page": 1, "pageSize": 5,
      "_notice": "Magnet links, info hashes, and torrent downloads require a free API key. Register at https://torrentclaw.com/register",
      "results": [{
        "id": 73263, "imdbId": "tt33764258", "tmdbId": "1368337",
        "contentType": "movie", "title": "The Odyssey", "titleOriginal": "The Odyssey",
        "matchedAlt": null, "year": 2026,
        "overview": "Odysseus, the legendary King of Ithaca, embarks on a long and perilous journey home.",
        "posterUrl": "https://image.tmdb.org/t/p/w500/5rhTDKUhPYvpdQIijFIs5VoWsON.jpg",
        "backdropUrl": "https://image.tmdb.org/t/p/w1280/mIi99BK2mDpLp2lGsa8jCBDFit4.jpg",
        "genres": ["Adventure", "Action", "Fantasy"],
        "originCountry": ["GB", "US"],
        "rating": "8.0", "ratingImdb": "8.0", "ratingTmdb": "8.0",
        "contentUrl": "/movies/the-odyssey-2026-73263",
        "hasTorrents": true,
        "torrents": [{
          "rawTitle": "The.Odyssey.2026.2160p.10bit.HDR.BluRay.6CH.x265.HEVC-PSA",
          "quality": "2160p", "codec": "hevc", "sourceType": "bluray",
          "sizeBytes": 5444395466, "seeders": 373, "leechers": 1098,
          "source": "bitmagnet", "qualityScore": 92,
          "uploadedAt": "2026-11-08T20:56:16.364Z", "languages": ["en"],
          "audioCodec": "aac",
          "audioTracks": [{"lang": "en", "codec": "aac", "title": "", "default": true, "profile": "HE-AAC", "channels": 6}],
          "subtitleTracks": [{"lang": "en", "codec": "subrip", "title": "", "forced": false, "default": true}],
          "videoInfo": {"hdr": "HDR10", "codec": "hevc", "width": 3840, "height": 2160, "profile": "Main 10", "bitDepth": 10, "duration": 10355.795, "frameRate": 23.976},
          "scanStatus": "success", "threatLevel": "clean", "torrentFiles": null,
          "hdrType": "HDR10", "audioChannels": 6, "releaseGroup": "PSA",
          "isProper": false, "isRepack": false, "isRemastered": false,
          "season": null, "episode": null, "packEpisodes": null,
          "subtitleLanguages": ["en", "fr-CA", "es-419"]
        }, {
          "rawTitle": "Odyssea / The Odyssey (2026)[2160p][HDR]",
          "quality": "2160p", "codec": "hevc", "sourceType": "bluray",
          "sizeBytes": 14098230149, "seeders": 110, "leechers": 109,
          "source": "torrentio:ext", "qualityScore": 91, "uploadedAt": null,
          "languages": ["en"], "audioCodec": "eac3",
          "audioTracks": [{"lang": "en", "codec": "eac3", "title": "", "default": false, "channels": 6}],
          "subtitleTracks": [],
          "videoInfo": {"hdr": "HDR10", "codec": "hevc", "width": 3840, "height": 2160, "profile": "Main 10", "bitDepth": 10, "duration": 10355.721, "frameRate": 23.976},
          "scanStatus": "success", "threatLevel": "clean", "torrentFiles": null,
          "hdrType": "HDR10", "audioChannels": 6, "releaseGroup": null,
          "isProper": false, "isRepack": false, "isRemastered": false,
          "season": null, "episode": null, "packEpisodes": null,
          "subtitleLanguages": ["cs", "en"]
        }, {
          "rawTitle": "The Odyssey (2026) 2160p bluray",
          "quality": "2160p", "codec": "hevc", "sourceType": "bluray",
          "sizeBytes": 8396661064, "seeders": 2621, "leechers": 791,
          "source": "yts", "qualityScore": 91, "uploadedAt": "2026-11-10T23:44:00.000Z",
          "languages": ["en"], "audioCodec": "aac",
          "audioTracks": [{"lang": "und", "codec": "aac", "title": "[detected:en(36%)]", "default": true, "profile": "LC", "channels": 6}],
          "subtitleTracks": [{"lang": "en", "codec": "subrip", "title": "", "forced": false, "default": true}],
          "videoInfo": {"hdr": "", "codec": "hevc", "width": 3840, "height": 2160, "profile": "Main", "bitDepth": 0, "frameRate": 23.976},
          "scanStatus": "success", "threatLevel": null, "torrentFiles": null,
          "hdrType": null, "audioChannels": null, "releaseGroup": null,
          "isProper": false, "isRepack": false, "isRemastered": false,
          "season": null, "episode": null, "packEpisodes": null,
          "subtitleLanguages": []
        }, {
          "rawTitle": "The Odyssey (2026) (2160p UHD BluRay x265 HDR DTS-HD MA 5.1 r00t) [QxR]",
          "quality": "2160p", "codec": "hevc", "sourceType": "bluray",
          "sizeBytes": 27963931553, "seeders": 345, "leechers": 233,
          "source": "piratebay", "qualityScore": 91, "uploadedAt": "2026-11-12T01:50:03.000Z",
          "languages": ["en"], "audioCodec": "dts",
          "audioTracks": [{"lang": "en", "codec": "dts", "title": "Surround 5.1", "default": true, "profile": "DTS-HD MA", "channels": 6}],
          "subtitleTracks": [], "videoInfo": null,
          "scanStatus": "success", "threatLevel": "clean", "torrentFiles": null,
          "hdrType": "HDR10", "audioChannels": 6, "releaseGroup": "QxR",
          "isProper": true, "isRepack": false, "isRemastered": false,
          "season": null, "episode": null, "packEpisodes": null,
          "subtitleLanguages": ["en"]
        }]
      }]
    }
    """.trimIndent()

    @Test
    fun `the full live payload decodes`() {
        val response = json.decodeFromString<SearchResponseDto>(livePayload)
        assertEquals(1022, response.total)
        assertEquals(1, response.results.size)
        assertEquals(4, response.results.first()!!.torrents.size)
    }

    @Test
    fun `every live release survives mapping, so the detail screen cannot show 'no torrents'`() {
        val response = json.decodeFromString<SearchResponseDto>(livePayload)
        val result = response.results.first()!!
        val detail = result.toMovieDetail(magnetResolver)

        assertEquals("The Odyssey", detail.movie.title)
        assertEquals(73263, detail.movie.id)
        assertEquals(
            "all four releases must reach the screen",
            4,
            detail.releases.size,
        )

        // Sorted by swarm health, so the 2621-seeder release is first.
        assertEquals(2621, detail.releases.first().seeders)
        assertTrue(detail.releases.all { it.sizeBytes != null && it.sizeBytes!! > 0 })
        // No key: hashes are absent, and the UI explains that instead of hiding rows.
        assertTrue(detail.releases.all { it.infoHash == null })
        assertTrue(detail.releases.none { it.isDownloadable })
    }

    @Test
    fun `unknown nested fields are ignored rather than failing the response`() {
        // audioTracks/subtitleTracks/videoInfo/torrentFiles are not in the model.
        val response = json.decodeFromString<SearchResponseDto>(livePayload)
        assertEquals(373, response.results.first()!!.torrents.first()!!.seeders)
    }

    @Test
    fun `a keyed payload with magnets produces downloadable releases`() {
        // Insert a real keyed torrent (hash + magnet + torrent URL) alongside the
        // anonymous ones, the shape the API returns once a key is present.
        val keyed = livePayload.replaceFirst(
            "\"torrents\": [{",
            "\"torrents\": [{" +
                "\"infoHash\": \"aabbccdd00112233445566778899aabbccddeeff\"," +
                "\"magnetUrl\": \"magnet:?xt=urn:btih:aabbccdd00112233445566778899aabbccddeeff&dn=The.Odyssey\"," +
                "\"torrentUrl\": \"https://torrentclaw.com/api/v1/torrent/aabbccdd00112233445566778899aabbccddeeff\"," +
                "\"rawTitle\": \"The.Odyssey.2026.KEYED.2160p\", \"quality\": \"2160p\"," +
                "\"sizeBytes\": 8396661064, \"seeders\": 2621, \"leechers\": 791," +
                "\"source\": \"yts\", \"languages\": [\"en\"]},{",
        )
        val detail = json.decodeFromString<SearchResponseDto>(keyed).results.first()!!
            .toMovieDetail(magnetResolver)

        val downloadable = detail.releases.firstOrNull { it.isDownloadable }
        assertTrue("the keyed release must be downloadable", downloadable != null)
        assertEquals("aabbccdd00112233445566778899aabbccddeeff", downloadable!!.infoHash)
        assertTrue(downloadable.playableMagnet!!.startsWith("magnet:?xt=urn:btih:"))
        // Hashless rows stay visible but locked, rather than disappearing.
        assertEquals(4, detail.releases.count { !it.isDownloadable })
    }
}
