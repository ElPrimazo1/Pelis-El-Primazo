package com.example.peliselprimazo.data.local.dao

import androidx.room.*
import com.example.peliselprimazo.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = 1")
    fun getUser(): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("UPDATE users SET profilePictureUri = :uri WHERE id = 1")
    suspend fun updateProfilePicture(uri: String?)

    @Query("UPDATE users SET isLoggedIn = :isLoggedIn WHERE id = 1")
    suspend fun updateLoginStatus(isLoggedIn: Boolean)

    @Query("DELETE FROM users")
    suspend fun deleteUser()
}
