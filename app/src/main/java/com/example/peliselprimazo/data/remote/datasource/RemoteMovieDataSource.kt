package com.example.peliselprimazo.data.remote.datasource

import com.example.peliselprimazo.BuildConfig
import com.example.peliselprimazo.data.remote.JikanApi
import com.example.peliselprimazo.data.remote.StreamtapeApi
import com.example.peliselprimazo.data.remote.TmdbApi
import com.example.peliselprimazo.domain.model.CastMember
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class TmdbData(
    val title: String,
    val originalTitle: String?,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val rating: Double,
    val genres: List<String>,
    val trailerUrl: String?,
    val cast: List<CastMember>,
    val duration: String?
)

@Singleton
class RemoteMovieDataSource @Inject constructor(
    private val streamtapeApi: StreamtapeApi,
    private val tmdbApi: TmdbApi,
    private val jikanApi: JikanApi
) {
    private val streamtapeLogin = BuildConfig.STREAMTAPE_LOGIN
    private val streamtapeKey = BuildConfig.STREAMTAPE_KEY

    suspend fun listStreamtapeFolder(folderId: String?) = 
        streamtapeApi.listFolder(streamtapeLogin, streamtapeKey, folderId)

    suspend fun getStreamtapeDownloadTicket(fileId: String) =
        streamtapeApi.getDownloadTicket(fileId, streamtapeLogin, streamtapeKey)

    suspend fun getStreamtapeDownloadLink(fileId: String, ticket: String) =
        streamtapeApi.getDownloadLink(fileId, ticket)

    suspend fun hitStreamtapeEmbed(fileId: String) {
        try {
            (URL("https://streamtape.com/e/$fileId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                connect()
                disconnect()
            }
        } catch (_: Exception) {}
    }

    suspend fun searchJikanAnime(query: String) = jikanApi.searchAnime(query)
    
    suspend fun getJikanAnimeFull(id: Int) = jikanApi.getAnimeFull(id)

    suspend fun searchTmdbTv(query: String, year: String?) = tmdbApi.searchTv(query, year)
    
    suspend fun searchTmdbMovie(query: String, year: String?) = tmdbApi.searchMovie(query, year)
    
    suspend fun getTmdbTvDetails(id: Int) = tmdbApi.getTvDetails(id)
    
    suspend fun getTmdbMovieDetails(id: Int) = tmdbApi.getMovieDetails(id)
}
