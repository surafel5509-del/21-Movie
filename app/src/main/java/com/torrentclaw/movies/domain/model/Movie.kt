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

/**
 * A single torrent release for a movie.
 *
 * [infoHash] is nullable on purpose: TorrentClaw's anonymous tier strips info
 * hashes, magnet URIs and `.torrent` URLs from the payload. Those rows are still
 * real, ranked releases, so the UI lists them — it just cannot download them
 * until the user adds a free API key. Dropping them (the previous behaviour)
 * made every movie look like "no releases".
 */
data class TorrentRelease(
    val infoHash: String?,
    val title: String?,
    val quality: String?,
    val codec: String?,
    val source: String?,
    val sizeBytes: Long?,
    val seeders: Int,
    val leechers: Int,
    val magnetUri: String?,
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

    /**
     * Stable identity for this release, safe to use as a LazyColumn key.
     *
     * The info hash is ideal when TorrentClaw exposes one. On the anonymous tier
     * several releases can share a `rawTitle` (or have none at all), so the
     * fallback folds in size, seeders, quality and group — fields that differ
     * between distinct release rows. Keying on the title alone crashed
     * `LazyColumn` with duplicate keys.
     */
    val key: String
        get() = infoHash?.lowercase() ?: buildString {
            append("t:")
            append(title.orEmpty())
            append('|').append(quality.orEmpty())
            append('|').append(sizeBytes ?: 0L)
            append('|').append(seeders)
            append('|').append(leechers)
            append('|').append(releaseGroup.orEmpty())
            append('|').append(tracker.orEmpty())
        }.hashCode().toUInt().toString(16).let { "local-" + it }

    /** Raw `.torrent` bytes, populated by the repository when it prefetches one. */
    var torrentBytes: ByteArray? = null
        private set

    /** Attaches the downloaded `.torrent` payload so the engine can skip metadata. */
    fun withTorrentBytes(bytes: ByteArray?): TorrentRelease = apply { torrentBytes = bytes }

    /** True once the release can be opened, copied or downloaded. */
    val isDownloadable: Boolean
        get() = !infoHash.isNullOrBlank() || !magnetUri.isNullOrBlank() || !torrentUrl.isNullOrBlank()

    /** Magnet link, built from the hash when the API withheld one. */
    val playableMagnet: String?
        get() = magnetUri?.takeIf { it.startsWith("magnet:?", ignoreCase = true) }
            ?: infoHash?.takeIf { it.isNotBlank() }?.let { buildMagnet(it, title) }

    val shortInfoHash: String
        get() {
            val hash = infoHash.orEmpty()
            return if (hash.length > 10) "${hash.take(6)}…${hash.takeLast(4)}" else hash
        }

    companion object {
        /** Public trackers appended when we have to build a magnet ourselves. */
        val DEFAULT_TRACKERS: List<String> = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.moeking.me:6969/announce",
            "udp://tracker.torrentclaw.com:1337/announce",
        )

        fun buildMagnet(infoHash: String, displayName: String?): String {
            val hash = infoHash.trim().lowercase()
            val name = displayName?.trim().orEmpty().ifBlank { hash }
            return buildString {
                append("magnet:?xt=urn:btih:").append(hash)
                append("&dn=").append(java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
                DEFAULT_TRACKERS.forEach {
                    append("&tr=").append(java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20"))
                }
            }
        }
    }
}

enum class SwarmHealth { STRONG, FAIR, WEAK, DEAD }

/** Why a detail payload has no releases, so the UI can say something useful. */
enum class EmptyReleasesReason {
    /** The API answered for this exact film and genuinely lists no torrents. */
    NOT_INDEXED_YET,

    /** Metadata resolved, but the API withheld hashes/magnets (no API key). */
    NEEDS_API_KEY,
}

/** Detail payload: the movie plus every release TorrentClaw knows about. */
data class MovieDetail(
    val movie: Movie,
    val releases: List<TorrentRelease>,
    val director: String? = null,
    val cast: List<CastMember> = emptyList(),
    /** Set only when [releases] is empty and the cause is known. */
    val emptyReason: EmptyReleasesReason? = null,
)

data class CastMember(
    val name: String,
    val character: String?,
    val profileUrl: String?,
)
