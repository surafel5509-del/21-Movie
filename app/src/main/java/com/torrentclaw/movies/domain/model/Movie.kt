package com.torrentclaw.movies.domain.model

import java.util.Locale

/**
 * A movie as the rest of the app sees it: display-ready TMDB metadata that may
 * come from the network, from Room (watchlist) or from a deep link.
 *
 * [id] is the TorrentClaw content id. Posters/backdrops are absolute HTTPS URLs.
 */
data class Movie(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val ratingImdb: Double?,
    val ratingTmdb: Double?,
    val genres: List<String>,
    val contentUrl: String?,
    val imdbId: String?,
    val tmdbId: String?,
    val maxSeeders: Int,
    val bestQuality: String?,
    val hasHdr: Boolean,
    val hasTorrents: Boolean,
) {
    /** Rating used by the badges: IMDb first (wider coverage), then TMDB. */
    val displayRating: Double?
        get() = ratingImdb ?: ratingTmdb

    val ratingLabel: String?
        get() = displayRating?.let { String.format(Locale.US, "%.1f", it) }

    val yearLabel: String
        get() = year?.toString() ?: "—"
}

/** A single torrent release for a movie. [magnetUri] is never blank once mapped. */
data class TorrentRelease(
    val infoHash: String,
    val title: String?,
    val quality: String?,
    val codec: String?,
    val source: String?,
    val sizeBytes: Long?,
    val seeders: Int,
    val leechers: Int,
    val magnetUri: String,
    val torrentUrl: String?,
    val tracker: String?,
    val qualityScore: Int?,
    val uploadedAt: String?,
    val releaseGroup: String?,
    val hdrType: String?,
    val audioCodec: String?,
    val languages: List<String>,
    val isProper: Boolean,
    val isRepack: Boolean,
) {
    /**
     * Short label shown on the quality badge, e.g. "1080p BluRay", "4K HDR".
     * Falls back gracefully when the indexer did not report a resolution.
     */
    val qualityLabel: String
        get() {
            val resolution = when (quality?.lowercase()) {
                "2160p", "4k", "uhd" -> "4K"
                null, "" -> ""
                else -> quality
            }
            val suffix = when {
                hdrType != null -> "HDR"
                source != null -> source
                codec != null -> codec
                else -> null
            }
            return listOfNotNull(resolution.takeIf { it.isNotBlank() }, suffix)
                .joinToString(" ")
                .ifBlank { quality ?: "—" }
        }

    /** Colour-coded health buckets: green above 50 seeders, amber above 10, red below. */
    val health: SwarmHealth
        get() = when {
            seeders >= 50 -> SwarmHealth.STRONG
            seeders >= 10 -> SwarmHealth.FAIR
            seeders > 0 -> SwarmHealth.WEAK
            else -> SwarmHealth.DEAD
        }

    val shortInfoHash: String
        get() = if (infoHash.length > 10) "${infoHash.take(6)}…${infoHash.takeLast(4)}" else infoHash
}

enum class SwarmHealth { STRONG, FAIR, WEAK, DEAD }

/** Detail payload: the movie plus every release TorrentClaw knows about. */
data class MovieDetail(
    val movie: Movie,
    val releases: List<TorrentRelease>,
    val director: String? = null,
    val cast: List<CastMember> = emptyList(),
)

data class CastMember(
    val name: String,
    val character: String?,
    val profileUrl: String?,
)
