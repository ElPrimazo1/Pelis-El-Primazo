package com.example.peliselprimazo.data.extractor

import com.example.peliselprimazo.domain.repository.MovieRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interfaz base para todos los extractores de video
 */
interface StreamExtractor {
    suspend fun extract(fileId: String, repository: MovieRepository): String?
    fun getHeaders(fileId: String): Map<String, String> = emptyMap()
}

/**
 * Extractor para Streamtape
 */
class StreamtapeExtractor : StreamExtractor {
    override suspend fun extract(fileId: String, repository: MovieRepository): String? {
        return repository.getDownloadUrl("Streamtape", fileId)
    }

    override fun getHeaders(fileId: String): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Referer" to "https://streamtape.com/",
            "Origin" to "https://streamtape.com"
        )
    }
}

/**
 * Extractor de respaldo (Fallback)
 */
class DefaultStreamExtractor(private val serverName: String) : StreamExtractor {
    override suspend fun extract(fileId: String, repository: MovieRepository): String? {
        return repository.getDownloadUrl(serverName, fileId)
    }

    override fun getHeaders(fileId: String): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        )
    }
}

/**
 * Fábrica inyectable con Hilt para seleccionar el extractor adecuado
 */
@Singleton
class StreamExtractorFactory @Inject constructor() {
    fun getExtractor(serverName: String): StreamExtractor {
        val name = serverName.lowercase().trim()
        return when {
            name.contains("streamtape") -> StreamtapeExtractor()
            else -> DefaultStreamExtractor(serverName)
        }
    }
}
