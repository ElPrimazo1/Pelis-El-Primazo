package com.example.peliselprimazo.domain.model

enum class ContentType {
    MOVIE, TV, ANIME
}

data class CastMember(
    val name: String,
    val character: String,
    val profilePath: String?
)

data class Subtitle(
    val url: String,
    val label: String,
    val language: String
)

data class Movie(
    val id: Int,
    val title: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: String? = null,
    val rating: Double = 0.0,
    val serverLinks: List<ServerLink>,
    val contentType: ContentType,
    val genres: List<String> = emptyList(),
    val trailerUrl: String? = null,
    val cast: List<CastMember> = emptyList(),
    val subtitles: List<Subtitle> = emptyList(),
    val year: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val duration: String? = null,
    val lastPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val isLiked: Boolean = false,
    val isWatchLater: Boolean = false,
    val isWatching: Boolean = false,
    val isSaved: Boolean = false,
    val isFinished: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
