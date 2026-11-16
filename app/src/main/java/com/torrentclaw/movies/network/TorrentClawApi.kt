package com.torrentclaw.movies.network

import com.torrentclaw.movies.network.dto.CreditsResponseDto
import com.torrentclaw.movies.network.dto.PopularResponseDto
import com.torrentclaw.movies.network.dto.SearchResponseDto
import com.torrentclaw.movies.network.dto.TrackRequestDto
import com.torrentclaw.movies.network.dto.TrackResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TorrentClaw public API. Base URL is `https://torrentclaw.com/`.
 *
 * Rate limits (per minute): search 30 anon / 120 with key, popular 30,
 * autocomplete 60, credits 30, track 60. The UI (400 ms debounce, single-flight
 * loads, 429 back-off) is tuned to stay inside those budgets.
 */
interface TorrentClawApi {

    /** Trending movies ranked by community engagement. `limit` is 1..24. */
    @GET("api/v1/popular")
    suspend fun getPopular(
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
        @Query("page") page: Int = 1,
        @Query("locale") locale: String? = null,
    ): Response<PopularResponseDto>

    /** Recently indexed content. `limit` is 1..24. */
    @GET("api/v1/recent")
    suspend fun getRecent(
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
        @Query("page") page: Int = 1,
        @Query("locale") locale: String? = null,
    ): Response<com.torrentclaw.movies.network.dto.RecentResponseDto>

    /**
     * Search. `q` is required; `quality` is one of 480p|720p|1080p|2160p and
     * `sort` is one of relevance|seeders|year|rating|added.
     */
    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "movie",
        @Query("quality") quality: String? = null,
        @Query("sort") sort: String = "relevance",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = SEARCH_PAGE_SIZE,
        @Query("locale") locale: String? = null,
    ): Response<SearchResponseDto>

    /**
     * The API has no `/movie/{id}` route, so the detail screen resolves content
     * through `contentUrl` (e.g. `/movies/inception-2010-1`) which returns the
     * full metadata envelope with every torrent attached.
     */
    @GET("api/v1/search")
    suspend fun getByContentUrl(
        @Query("q") title: String,
        @Query("type") type: String = "movie",
        @Query("limit") limit: Int = 5,
        @Query("locale") locale: String? = null,
    ): Response<SearchResponseDto>

    @GET("api/v1/content/{id}/credits")
    suspend fun getCredits(
        @Path("id") contentId: Int,
    ): Response<CreditsResponseDto>

    /** Anonymous analytics ping; failures are ignored by the caller. */
    @POST("api/v1/track")
    suspend fun track(
        @Body body: TrackRequestDto,
    ): Response<TrackResponseDto>

    companion object {
        const val DEFAULT_PAGE_SIZE = 24
        const val SEARCH_PAGE_SIZE = 24
    }
}
