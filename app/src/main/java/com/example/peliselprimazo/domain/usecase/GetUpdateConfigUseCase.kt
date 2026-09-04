package com.example.peliselprimazo.domain.usecase

import com.example.peliselprimazo.domain.model.UpdateConfig
import com.example.peliselprimazo.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUpdateConfigUseCase @Inject constructor(
    private val repository: ConfigRepository
) {
    operator fun invoke(): Flow<UpdateConfig> = repository.getUpdateConfig()
    
    suspend fun sync() = repository.fetchAndActivate()
}
