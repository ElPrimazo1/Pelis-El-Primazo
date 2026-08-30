package com.example.peliselprimazo.data.local.dao

import androidx.room.*
import com.example.peliselprimazo.data.local.entities.UserContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserContentDao {
    @Query("SELECT * FROM user_content ORDER BY timestamp DESC")
    fun getAllUserContent(): Flow<List<UserContentEntity>>

    @Query("SELECT * FROM user_content WHERE isLiked = 1 ORDER BY timestamp DESC")
    fun getLikedContent(): Flow<List<UserContentEntity>>

    @Query("SELECT * FROM user_content WHERE isWatchLater = 1 ORDER BY timestamp DESC")
    fun getWatchLaterContent(): Flow<List<UserContentEntity>>

    @Query("SELECT * FROM user_content WHERE isWatching = 1 ORDER BY timestamp DESC")
    fun getWatchingContent(): Flow<List<UserContentEntity>>

    @Query("SELECT * FROM user_content WHERE isSaved = 1 ORDER BY timestamp DESC")
    fun getSavedContent(): Flow<List<UserContentEntity>>

    @Query("SELECT * FROM user_content WHERE id = :id")
    suspend fun getContentById(id: Int): UserContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(content: UserContentEntity)

    @Delete
    suspend fun delete(content: UserContentEntity)
}
