package com.example.peliselprimazo.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.peliselprimazo.domain.model.CastMember
import com.example.peliselprimazo.domain.model.ServerLink

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val originalTitle: String? = null,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val rating: Double,
    val contentType: String,
    val year: String?,
    val genres: List<String>,
    val serverLinks: List<ServerLink>,
    val trailerUrl: String?,
    val cast: List<CastMember>,
    val season: Int?,
    val episode: Int?,
    val duration: String? = null,
    // User interaction flags
    val isLiked: Boolean = false,
    val isWatchLater: Boolean = false,
    val isWatching: Boolean = false,
    val isSaved: Boolean = false,
    val isFinished: Boolean = false,
    val lastPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
