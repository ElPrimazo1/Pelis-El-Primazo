package com.example.peliselprimazo.di

import com.example.peliselprimazo.data.repository.ConfigRepositoryImpl
import com.example.peliselprimazo.data.repository.MovieRepositoryImpl
import com.example.peliselprimazo.domain.repository.ConfigRepository
import com.example.peliselprimazo.domain.repository.MovieRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMovieRepository(
        movieRepositoryImpl: MovieRepositoryImpl
    ): MovieRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(
        configRepositoryImpl: ConfigRepositoryImpl
    ): ConfigRepository
}
