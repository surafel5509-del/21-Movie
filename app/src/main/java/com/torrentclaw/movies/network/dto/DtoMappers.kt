package com.torrentclaw.movies.network.dto

import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.MovieDetail
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.domain.model.TorrentRelease
import com.torrentclaw.movies.network.MagnetResolver
import java.util.Locale

/**
 * DTO → domain mapping. This is the single place where the loosely-typed
 * TorrentClaw payload (stringified numbers, optional fields, ISO dates) becomes
 * a strict, display-ready model.
 */

/** IMDb/TMDB ratings arrive as strings such as "8.8"; parse leniently. */
fun String?.toRating(): Double? = this
    ?.trim()
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.takeIf { it in 0.0..10.0 }

/** `sizeBytes` is a numeric string on the wire and may exceed Int range. */
fun String?.toSizeBytes(): Long? = this?.trim()?.toLongOrNull()?.takeIf { it > 0 }

fun String?.toTmdbId(): String? = this?.trim()?.takeIf { it.isNotBlank() }

fun SearchResultDto.toMovie(): Movie = Movie(
    id = id,
    title = title.ifBlank { titleOriginal.orEmpty() },
    originalTitle = titleOriginal,
    overview = overview?.trim()?.takeIf { it.isNotBlank() },
    posterUrl = posterUrl.toHttpsImage(),
    backdropUrl = backdropUrl.toHttpsImage(),
    year = year?.takeIf { it in 1888..2100 },
    ratingImdb = ratingImdb.toRating(),
    ratingTmdb = ratingTmdb.toRating(),
    genres = genres.distinct(),
    contentUrl = contentUrl,
    imdbId = imdbId,
    tmdbId = tmdbId.toTmdbId(),
    maxSeeders = maxOf(maxSeeders, torrents.maxOfOrNull { it.seeders } ?: 0),
    bestQuality = bestQuality ?: bestQualityLabel(),
    hasHdr = hasHdr == true || torrents.any { !it.hdrType.isNullOrBlank() },
    hasTorrents = hasTorrents || torrents.isNotEmpty(),
)

fun PopularItemDto.toMovie(): Movie = Movie(
    id = id,
    title = title,
    originalTitle = null,
    overview = overview?.trim()?.takeIf { it.isNotBlank() },
    posterUrl = posterUrl.toHttpsImage(),
    backdropUrl = backdropUrl.toHttpsImage(),
    year = year?.takeIf { it in 1888..2100 },
    ratingImdb = ratingImdb.toRating(),
    ratingTmdb = ratingTmdb.toRating(),
    genres = genres.distinct(),
    contentUrl = null,
    imdbId = null,
    tmdbId = null,
    maxSeeders = maxSeeders,
    bestQuality = bestQuality,
    hasHdr = hasHdr == true,
    hasTorrents = maxSeeders > 0 || topInfoHash != null,
)

fun RecentItemDto.toMovie(): Movie = Movie(
    id = id,
    title = title,
    originalTitle = null,
    overview = overview?.trim()?.takeIf { it.isNotBlank() },
    posterUrl = posterUrl.toHttpsImage(),
    backdropUrl = backdropUrl.toHttpsImage(),
    year = year?.takeIf { it in 1888..2100 },
    ratingImdb = ratingImdb.toRating(),
    ratingTmdb = ratingTmdb.toRating(),
    genres = genres.distinct(),
    contentUrl = null,
    imdbId = null,
    tmdbId = null,
    maxSeeders = maxSeeders,
    bestQuality = bestQuality,
    hasHdr = hasHdr == true,
    hasTorrents = true,
)

/**
 * Maps a release, rebuilding the magnet when the payload has one but no hash.
 *
 * Important: this never returns null. TorrentClaw's anonymous tier sends real
 * releases with `infoHash`, `magnetUrl` and `torrentUrl` omitted, and the old
 * behaviour of dropping those rows is what made every movie render
 * "No torrents indexed yet". Metadata is preserved so the release still shows
 * up, sorted and ranked, with download actions disabled until a key is added.
 */
fun TorrentDto.toRelease(magnetResolver: MagnetResolver, movieTitle: String): TorrentRelease {
    val hash = infoHash.trim().ifBlank { null }
    val magnet = magnetResolver.resolve(
        magnetUrl = magnetUrl,
        infoHash = infoHash,
        displayName = rawTitle ?: movieTitle,
    )

    return TorrentRelease(
        infoHash = hash,
        title = rawTitle?.trim()?.takeIf { it.isNotBlank() },
        quality = quality?.trim()?.takeIf { it.isNotBlank() },
        codec = codec?.trim()?.takeIf { it.isNotBlank() },
        source = sourceType?.trim()?.takeIf { it.isNotBlank() },
        sizeBytes = sizeBytes.toSizeBytes(),
        seeders = seeders.coerceAtLeast(0),
        leechers = leechers.coerceAtLeast(0),
        magnetUri = magnet?.takeIf { it.startsWith("magnet:?", ignoreCase = true) },
        // The API may expose either an absolute URL or only the hash, in which
        // case the documented /api/v1/torrent/{infoHash} route is used.
        torrentUrl = torrentUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: hash?.let { "https://torrentclaw.com/api/v1/torrent/$it" },
        tracker = source?.trim()?.takeIf { it.isNotBlank() },
        qualityScore = qualityScore,
        uploadedAt = uploadedAt,
        releaseGroup = releaseGroup,
        hdrType = hdrType,
        audioCodec = audioCodec,
        languages = languages,
        isProper = isProper == true,
        isRepack = isRepack == true,
    )
}

/** Detail payload = movie metadata + de-duplicated releases sorted by swarm health. */
fun SearchResultDto.toMovieDetail(magnetResolver: MagnetResolver): MovieDetail {
    val releases = torrents
        .map { it.toRelease(magnetResolver, movieTitle = title) }
        .distinctBy { it.infoHash?.lowercase() ?: it.title.orEmpty().lowercase() }
        .sortedByDescending { it.seeders }
    return MovieDetail(movie = toMovie(), releases = releases)
}

/** Deep-link/document parser used when only a magnet URI is available. */
private val INFO_HASH_REGEX = Regex("(?i)urn:btih:([a-z0-9]{32,40})")

fun extractInfoHash(magnet: String): String? =
    INFO_HASH_REGEX.find(magnet)?.groupValues?.getOrNull(1)

/** Sorts releases for the detail screen; "Size" is not a server-side sort. */
fun List<TorrentRelease>.sortedFor(sort: SortOption): List<TorrentRelease> = when (sort) {
    SortOption.SEEDERS -> sortedWith(compareByDescending<TorrentRelease> { it.seeders }.thenByDescending { it.sizeBytes ?: 0L })
    SortOption.NEWEST -> sortedWith(compareByDescending<TorrentRelease> { it.uploadedAt.orEmpty() })
    SortOption.SIZE -> sortedByDescending { it.sizeBytes ?: 0L }
}

/** Absolute HTTPS URL or null — TMDB poster paths are normally absolute already. */
private fun String?.toHttpsImage(): String? {
    val value = this?.trim().orEmpty()
    return when {
        value.startsWith("https://") -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://image.tmdb.org/t/p/w500$value"
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        value.isBlank() -> null
        else -> null
    }
}

private fun SearchResultDto.bestQualityLabel(): String? {
    val best = torrents.mapNotNull { it.quality }.maxByOrNull { qualityRank(it) } ?: return null
    return if (qualityRank(best) >= qualityRank("2160p")) "4K" else best
}

private fun qualityRank(quality: String?): Int = when (quality?.lowercase(Locale.US)) {
    "2160p", "4k", "uhd" -> 4
    "1080p" -> 3
    "720p" -> 2
    "480p" -> 1
    else -> 0
}
