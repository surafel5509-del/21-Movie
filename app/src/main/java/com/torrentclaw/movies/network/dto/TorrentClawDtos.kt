package com.torrentclaw.movies.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the public TorrentClaw API (v1, https://torrentclaw.com/api/v1).
 *
 * Everything is nullable-tolerant on purpose: the aggregator merges ~10 upstream
 * indexers (YTS, EZTV, Knaben, Torrentio, DHT…) and any of them can omit a field.
 * Numbers that upstreams send as strings (`ratingImdb`, `sizeBytes`, `tmdbId`)
 * are declared as [String] and parsed defensively in `dto/DtoMappers.kt`.
 */

@Serializable
data class PopularResponseDto(
    val items: List<PopularItemDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("pageSize") val pageSize: Int = 0,
)

@Serializable
data class PopularItemDto(
    val id: Int,
    val title: String = "",
    val year: Int? = null,
    @SerialName("contentType") val contentType: String? = null,
    @SerialName("posterUrl") val posterUrl: String? = null,
    @SerialName("backdropUrl") val backdropUrl: String? = null,
    val overview: String? = null,
    @SerialName("ratingImdb") val ratingImdb: String? = null,
    @SerialName("ratingTmdb") val ratingTmdb: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("maxSeeders") val maxSeeders: Int = 0,
    @SerialName("clickCount") val clickCount: Int = 0,
    @SerialName("bestQuality") val bestQuality: String? = null,
    @SerialName("hasHdr") val hasHdr: Boolean? = null,
    @SerialName("topInfoHash") val topInfoHash: String? = null,
)

@Serializable
data class SearchResponseDto(
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("pageSize") val pageSize: Int = 0,
    @SerialName("parsedSeason") val parsedSeason: Int? = null,
    @SerialName("parsedEpisode") val parsedEpisode: Int? = null,
    @SerialName("fuzzyMatch") val fuzzyMatch: Boolean? = null,
    val results: List<SearchResultDto> = emptyList(),
)

@Serializable
data class SearchResultDto(
    val id: Int,
    @SerialName("imdbId") val imdbId: String? = null,
    @SerialName("tmdbId") val tmdbId: String? = null,
    @SerialName("contentType") val contentType: String = CONTENT_TYPE_MOVIE,
    val title: String = "",
    @SerialName("titleOriginal") val titleOriginal: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    @SerialName("posterUrl") val posterUrl: String? = null,
    @SerialName("backdropUrl") val backdropUrl: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("ratingImdb") val ratingImdb: String? = null,
    @SerialName("ratingTmdb") val ratingTmdb: String? = null,
    @SerialName("contentUrl") val contentUrl: String? = null,
    @SerialName("hasTorrents") val hasTorrents: Boolean = false,
    @SerialName("maxSeeders") val maxSeeders: Int = 0,
    @SerialName("bestQuality") val bestQuality: String? = null,
    @SerialName("hasHdr") val hasHdr: Boolean? = null,
    @SerialName("topInfoHash") val topInfoHash: String? = null,
    val torrents: List<TorrentDto> = emptyList(),
) {
    companion object {
        const val CONTENT_TYPE_MOVIE = "movie"
    }
}

/**
 * A single release. `infoHash` is the only mandatory field; `magnetUrl` is
 * optional because anonymous clients sometimes have to rebuild it locally,
 * which [com.torrentclaw.movies.network.MagnetResolver] does.
 */
@Serializable
data class TorrentDto(
    val infoHash: String = "",
    @SerialName("rawTitle") val rawTitle: String? = null,
    val quality: String? = null,
    val codec: String? = null,
    @SerialName("sourceType") val sourceType: String? = null,
    @SerialName("sizeBytes") val sizeBytes: String? = null,
    val seeders: Int = 0,
    val leechers: Int = 0,
    @SerialName("magnetUrl") val magnetUrl: String? = null,
    @SerialName("torrentUrl") val torrentUrl: String? = null,
    val source: String? = null,
    @SerialName("qualityScore") val qualityScore: Int? = null,
    @SerialName("scrapedAt") val scrapedAt: String? = null,
    @SerialName("uploadedAt") val uploadedAt: String? = null,
    val languages: List<String> = emptyList(),
    @SerialName("audioCodec") val audioCodec: String? = null,
    @SerialName("hdrType") val hdrType: String? = null,
    @SerialName("releaseGroup") val releaseGroup: String? = null,
    @SerialName("isProper") val isProper: Boolean? = null,
    @SerialName("isRepack") val isRepack: Boolean? = null,
    @SerialName("isRemastered") val isRemastered: Boolean? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

@Serializable
data class CreditsResponseDto(
    @SerialName("contentId") val contentId: Int = 0,
    val director: String? = null,
    val cast: List<CastMemberDto> = emptyList(),
)

@Serializable
data class CastMemberDto(
    val name: String = "",
    val character: String? = null,
    @SerialName("profileUrl") val profileUrl: String? = null,
)

/** `POST /api/v1/track` — fire-and-forget analytics for magnet usage. */
@Serializable
data class TrackRequestDto(
    val infoHash: String,
    val action: String,
)

@Serializable
data class TrackResponseDto(
    val ok: Boolean = false,
)

/** Non-2xx bodies look like `{ "error": "..." }`. */
@Serializable
data class ApiErrorDto(
    val error: String? = null,
    val message: String? = null,
)
