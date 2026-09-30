package com.example.peliselprimazo.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.example.peliselprimazo.data.local.entities.MovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY title ASC")
    fun getAllMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY lastUpdated DESC")
    fun getPagedMovies(): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE contentType = :type ORDER BY title ASC")
    fun getMoviesByType(type: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getMovieById(id: Int): MovieEntity?

    @Query("SELECT * FROM movies WHERE id = :id")
    fun getMovieFlowById(id: Int): Flow<MovieEntity?>
    
    @Query("SELECT * FROM movies WHERE isSaved = 1")
    suspend fun getSavedMoviesSync(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE title LIKE '%' || :query || '%'")
    fun searchMovies(query: String): Flow<List<MovieEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    @Query("DELETE FROM movies")
    suspend fun clearAllMovies()

    @Query("DELETE FROM movies WHERE lastUpdated < :timestamp")
    suspend fun deleteOldMovies(timestamp: Long)

    @Query("UPDATE movies SET isLiked = :isLiked WHERE id = :id")
    suspend fun updateLiked(id: Int, isLiked: Boolean)

    @Query("UPDATE movies SET isSaved = :isSaved WHERE id = :id")
    suspend fun updateSaved(id: Int, isSaved: Boolean)

    @Query("UPDATE movies SET isWatchLater = :isWatchLater WHERE id = :id")
    suspend fun updateWatchLater(id: Int, isWatchLater: Boolean)

    @Query("UPDATE movies SET isWatching = :isWatching, timestamp = :timestamp WHERE id = :id")
    suspend fun updateWatching(id: Int, isWatching: Boolean, timestamp: Long)

    @Query("UPDATE movies SET isFinished = :isFinished WHERE id = :id")
    suspend fun updateFinished(id: Int, isFinished: Boolean)

    @Query("UPDATE movies SET lastPosition = :position, totalDuration = :total WHERE id = :id")
    suspend fun updatePlaybackProgress(id: Int, position: Long, total: Long)
}
