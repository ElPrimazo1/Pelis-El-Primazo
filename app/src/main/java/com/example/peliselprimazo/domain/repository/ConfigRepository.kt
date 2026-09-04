package com.example.peliselprimazo.domain.repository

import com.example.peliselprimazo.domain.model.UpdateConfig
import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    fun getUpdateConfig(): Flow<UpdateConfig>
    suspend fun fetchAndActivate(): Boolean
}
