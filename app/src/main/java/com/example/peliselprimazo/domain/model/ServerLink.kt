package com.example.peliselprimazo.domain.model

data class ServerLink(
    val serverName: String,
    val fileId: String,
    val quality: String = "HD",
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val language: String? = null
)
