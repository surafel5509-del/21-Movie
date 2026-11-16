package com.torrentclaw.movies.network

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds magnet URIs for releases that arrive without one.
 *
 * TorrentClaw returns a server-built `magnetUrl` when the request is
 * authenticated. Anonymous clients occasionally get only the `infoHash`, so the
 * URI is rebuilt locally with the same public tracker set the TorrentClaw
 * clients use. This keeps "Open magnet" working on the free tier.
 */
@Singleton
class MagnetResolver @Inject constructor() {

    fun resolve(
        magnetUrl: String?,
        infoHash: String,
        displayName: String?,
    ): String? {
        val trimmed = magnetUrl?.trim().orEmpty()
        if (trimmed.startsWith("magnet:?", ignoreCase = true)) return trimmed
        if (infoHash.isBlank()) return null
        return build(infoHash, displayName)
    }

    fun build(infoHash: String, displayName: String?): String {
        // btih is hex; libtorrent's Sha1Hash.parseHex() rejects upper case input on
        // some paths, and two spellings of one hash would double-count the swarm.
        val hash = infoHash.trim().lowercase()
        val name = displayName?.trim().orEmpty().ifBlank { hash }
        return buildString {
            append("magnet:?xt=urn:btih:").append(hash)
            append("&dn=").append(encode(name))
            TRACKERS.forEach { append("&tr=").append(encode(it)) }
        }
    }

    /** True when [uri] looks like a magnet link the system can route. */
    fun isMagnet(uri: String?): Boolean =
        uri != null && uri.startsWith("magnet:?", ignoreCase = true) && uri.length > 20

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    companion object {
        /** Public, long-lived trackers so new magnets find peers immediately. */
        val TRACKERS: List<String> = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.moeking.me:6969/announce",
        )
    }
}
