package com.example.peliselprimazo.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface JikanApi {
    /**
     * Search anime by query.
     * sfw: Filter out Adult entries. Default is false for better discovery.
     */
    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5,
        @Query("sfw") sfw: Boolean = false
    ): JikanResponse

    /**
     * Get full details of an anime by ID.
     */
    @GET("anime/{id}/full")
    suspend fun getAnimeFull(
        @Path("id") id: Int
    ): JikanAnimeFullResponse

    companion object {
        const val BASE_URL = "https://api.jikan.moe/v4/"
    }
}

data class JikanResponse(
    @SerializedName("data") val data: List<JikanAnimeData>?
)

data class JikanAnimeFullResponse(
    @SerializedName("data") val data: JikanAnimeData?
)

data class JikanAnimeData(
    @SerializedName("mal_id") val id: Int?,
    @SerializedName("url") val url: String?,
    @SerializedName("images") val images: JikanImages?,
    @SerializedName("trailer") val trailer: JikanTrailer?,
    @SerializedName("title") val title: String?,
    @SerializedName("title_english") val titleEnglish: String?,
    @SerializedName("title_japanese") val titleJapanese: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("source") val source: String?,
    @SerializedName("episodes") val episodes: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("airing") val airing: Boolean?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("score") val score: Double?,
    @SerializedName("scored_by") val scoredBy: Int?,
    @SerializedName("rank") val rank: Int?,
    @SerializedName("popularity") val popularity: Int?,
    @SerializedName("members") val members: Int?,
    @SerializedName("favorites") val favorites: Int?,
    @SerializedName("synopsis") val synopsis: String?,
    @SerializedName("background") val background: String?,
    @SerializedName("season") val season: String?,
    @SerializedName("year") val year: Int?,
    @SerializedName("genres") val genres: List<JikanGenre>?
)

data class JikanImages(
    @SerializedName("jpg") val jpg: JikanImageFormat?,
    @SerializedName("webp") val webp: JikanImageFormat?
)

data class JikanImageFormat(
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("small_image_url") val smallImageUrl: String?,
    @SerializedName("large_image_url") val largeImageUrl: String?
)

data class JikanTrailer(
    @SerializedName("youtube_id") val youtubeId: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("embed_url") val embedUrl: String?,
    @SerializedName("images") val images: JikanTrailerImages?
)

data class JikanTrailerImages(
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("small_image_url") val smallImageUrl: String?,
    @SerializedName("medium_image_url") val mediumImageUrl: String?,
    @SerializedName("large_image_url") val largeImageUrl: String?,
    @SerializedName("maximum_image_url") val maximum_image_url: String?
)

data class JikanGenre(
    @SerializedName("mal_id") val malId: Int?,
    @SerializedName("type") val type: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("url") val url: String?
)
