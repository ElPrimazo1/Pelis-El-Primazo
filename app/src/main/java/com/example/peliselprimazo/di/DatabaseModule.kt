package com.example.peliselprimazo.di

import android.content.Context
import androidx.room.Room
import com.example.peliselprimazo.data.local.PelisDatabase
import com.example.peliselprimazo.data.local.dao.MovieDao
import com.example.peliselprimazo.data.local.dao.SearchHistoryDao
import com.example.peliselprimazo.data.local.dao.UserContentDao
import com.example.peliselprimazo.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PelisDatabase {
        return Room.databaseBuilder(
            context,
            PelisDatabase::class.java,
            PelisDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideUserContentDao(database: PelisDatabase): UserContentDao {
        return database.userContentDao
    }

    @Provides
    @Singleton
    fun provideMovieDao(database: PelisDatabase): MovieDao {
        return database.movieDao
    }

    @Provides
    @Singleton
    fun provideUserDao(database: PelisDatabase): UserDao {
        return database.userDao
    }

    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: PelisDatabase): SearchHistoryDao {
        return database.searchHistoryDao
    }
}
