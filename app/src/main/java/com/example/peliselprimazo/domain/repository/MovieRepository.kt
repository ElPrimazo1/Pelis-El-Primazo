package com.example.peliselprimazo.domain.repository

import com.example.peliselprimazo.domain.model.Movie
import com.example.peliselprimazo.domain.model.User
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData

interface MovieRepository {
    // Content fetching
    fun getAllContent(): Flow<List<Movie>>
    fun getPagedContent(): Flow<PagingData<Movie>>
    fun getMovies(): Flow<List<Movie>>
    fun getSeries(): Flow<List<Movie>>
    fun getTrends(): Flow<List<Movie>>
    
    suspend fun refreshContent()
    suspend fun getMovieById(id: Int): Movie?
    suspend fun getDownloadUrl(serverName: String, fileId: String): String?
    fun searchMovies(query: String): Flow<List<Movie>>

    // User Interaction (Local)
    fun getLikedContent(): Flow<List<Movie>>
    fun getWatchLaterContent(): Flow<List<Movie>>
    fun getWatchingContent(): Flow<List<Movie>>
    fun getSavedContent(): Flow<List<Movie>>
    fun getFinishedContent(): Flow<List<Movie>>
    
    suspend fun toggleLike(movieId: Int)
    suspend fun toggleWatchLater(movieId: Int)
    suspend fun toggleSave(movieId: Int)
    suspend fun toggleFinished(movieId: Int)
    suspend fun addToWatching(movieId: Int)
    suspend fun updatePlaybackProgress(movieId: Int, position: Long, duration: Long)
    suspend fun clearWatchingHistory()
    suspend fun clearCache()

    fun isMovieLiked(movieId: Int): Flow<Boolean>
    fun isMovieSaved(movieId: Int): Flow<Boolean>
    fun isMovieInWatchLater(movieId: Int): Flow<Boolean>
    fun isMovieFinished(movieId: Int): Flow<Boolean>

    // Search History
    fun getSearchHistory(): Flow<List<String>>
    suspend fun addSearchQuery(query: String)
    suspend fun clearSearchHistory()

    // Error Reporting
    suspend fun reportBrokenLink(movieId: Int, serverName: String, fileId: String)

    // Auth & Profile
    fun getUser(): Flow<User?>
    suspend fun register(username: String, email: String, password: String)
    suspend fun login(email: String, password: String): Boolean
    suspend fun logout()
    suspend fun updateAvatar(uri: String)
}
