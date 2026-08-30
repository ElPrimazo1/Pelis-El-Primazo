package com.example.peliselprimazo.data.model

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse<T>(
    @SerializedName("results") val results: List<T>
)

data class TmdbMovieResult(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?
)

data class TmdbTvResult(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?
)

data class TmdbMovieDetail(
    val id: Int,
    val genres: List<TmdbGenre>?,
    val videos: TmdbVideoResponse?,
    val credits: TmdbCredits?,
    val runtime: Int?
)

data class TmdbTvDetail(
    val id: Int,
    val genres: List<TmdbGenre>?,
    val videos: TmdbVideoResponse?,
    val credits: TmdbCredits?,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>?
)

data class TmdbGenre(val name: String)

data class TmdbVideoResponse(val results: List<TmdbVideo>)
data class TmdbVideo(val key: String, val site: String, val type: String)

data class TmdbCredits(val cast: List<TmdbCast>)
data class TmdbCast(val name: String, val character: String, val profile_path: String?)
