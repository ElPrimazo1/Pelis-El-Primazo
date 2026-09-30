package com.example.peliselprimazo.data.extractor

import android.util.Log
import com.example.peliselprimazo.domain.repository.MovieRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

interface StreamExtractor {
    suspend fun extract(fileId: String, repository: MovieRepository): Pair<String?, Map<String, String>>
    fun getHeaders(): Map<String, String>
}

@Singleton
class StreamExtractorFactory @Inject constructor(private val okHttpClient: OkHttpClient) {
    fun getExtractor(serverName: String): StreamExtractor {
        return when {
            serverName.lowercase().contains("streamtape") -> StreamtapeExtractor(okHttpClient)
            else -> DefaultStreamExtractor(serverName)
        }
    }
}

class StreamtapeExtractor(private val okHttpClient: OkHttpClient) : StreamExtractor {
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override suspend fun extract(fileId: String, repository: MovieRepository): Pair<String?, Map<String, String>> {
        val headers = mutableMapOf("User-Agent" to ua, "Referer" to "https://streamtape.com/")
        
        // 1. Intentar por API primero
        val apiUrl = repository.getDownloadUrl("Streamtape", fileId)
        if (!apiUrl.isNullOrBlank()) return apiUrl to headers

        // 2. Fallback: Scraping robusto del Embed
        return try {
            val request = Request.Builder()
                .url("https://streamtape.com/e/$fileId")
                .header("User-Agent", ua)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: return null to headers
                val cookies = response.headers("Set-Cookie").joinToString("; ") { it.split(";")[0] }
                if (cookies.isNotEmpty()) headers["Cookie"] = cookies

                // Localizar el enlace de video protegido por JS
                val match = Regex("document\\.getElementById\\('videolink'\\)\\.innerHTML = \"([^\"]+)\" \\+ '([^\"]+)'").find(html)
                val videoUrl = if (match != null) {
                    "https:" + match.groupValues[1] + match.groupValues[2]
                } else {
                    Regex("id=\"videolink\" style=\"display:none;\">([^<]+)</div>").find(html)?.groupValues?.get(1)?.let { "https:$it" }
                }
                
                videoUrl to headers
            }
        } catch (e: Exception) {
            Log.e("StreamtapeExtractor", "Error: ${e.message}")
            null to headers
        }
    }

    override fun getHeaders() = mapOf("User-Agent" to ua, "Referer" to "https://streamtape.com/")
}

class DefaultStreamExtractor(private val serverName: String) : StreamExtractor {
    override suspend fun extract(fileId: String, repository: MovieRepository): Pair<String?, Map<String, String>> {
        val url = repository.getDownloadUrl(serverName, fileId)
        return url to getHeaders()
    }
    override fun getHeaders() = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
}
