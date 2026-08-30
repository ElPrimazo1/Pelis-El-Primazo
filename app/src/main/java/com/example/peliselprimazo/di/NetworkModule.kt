package com.example.peliselprimazo.di

import com.example.peliselprimazo.BuildConfig
import com.example.peliselprimazo.data.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    @Named("StreamtapeRetrofit")
    fun provideStreamtapeRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(StreamtapeApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("TmdbRetrofit")
    fun provideTmdbRetrofit(loggingInterceptor: HttpLoggingInterceptor): Retrofit {
        val tmdbClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val apiKey = BuildConfig.TMDB_API_KEY
                
                val requestBuilder = original.newBuilder()
                
                if (apiKey.length > 50) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                } else {
                    val url = original.url.newBuilder()
                        .addQueryParameter("api_key", apiKey)
                        .build()
                    requestBuilder.url(url)
                }
                
                chain.proceed(requestBuilder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(TmdbApi.BASE_URL)
            .client(tmdbClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("MyBidRetrofit")
    fun provideMyBidRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(MyBidApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("ClickaduRetrofit")
    fun provideClickaduRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ClickaduApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideStreamtapeApi(@Named("StreamtapeRetrofit") retrofit: Retrofit): StreamtapeApi {
        return retrofit.create(StreamtapeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTmdbApi(@Named("TmdbRetrofit") retrofit: Retrofit): TmdbApi {
        return retrofit.create(TmdbApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMyBidApi(@Named("MyBidRetrofit") retrofit: Retrofit): MyBidApi {
        return retrofit.create(MyBidApi::class.java)
    }

    @Provides
    @Singleton
    fun provideClickaduApi(@Named("ClickaduRetrofit") retrofit: Retrofit): ClickaduApi {
        return retrofit.create(ClickaduApi::class.java)
    }
}
