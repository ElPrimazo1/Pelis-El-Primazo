package com.example.peliselprimazo.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_content")
data class UserContentEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val posterUrl: String?,
    val contentType: String,
    val rating: Double,
    val isLiked: Boolean = false,
    val isWatchLater: Boolean = false,
    val isWatching: Boolean = false,
    val isSaved: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
