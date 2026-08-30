package com.example.peliselprimazo.domain.model

data class User(
    val username: String = "",
    val email: String = "",
    val profilePictureUri: String? = null,
    val isLoggedIn: Boolean = false
)
