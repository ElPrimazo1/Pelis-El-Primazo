package com.example.peliselprimazo.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int = 1, // Single user app for now
    val username: String,
    val email: String,
    val password: String,
    val profilePictureUri: String? = null,
    val isLoggedIn: Boolean = false
)
