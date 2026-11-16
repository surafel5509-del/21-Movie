package com.torrentclaw.movies.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/recent` — newly indexed movies. */
@Serializable
data class RecentResponseDto(
    val items: List<RecentItemDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("pageSize") val pageSize: Int = 0,
)

@Serializable
data class RecentItemDto(
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
    @SerialName("bestQuality") val bestQuality: String? = null,
    @SerialName("hasHdr") val hasHdr: Boolean? = null,
    @SerialName("createdAt") val createdAt: String? = null,
)
