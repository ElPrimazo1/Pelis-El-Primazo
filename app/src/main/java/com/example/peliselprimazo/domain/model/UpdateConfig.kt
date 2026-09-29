package com.example.peliselprimazo.domain.model

data class UpdateConfig(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateUrl: String,
    val isUpdateAvailable: Boolean = false,
    val visualFlags: Map<String, String> = emptyMap()
)
