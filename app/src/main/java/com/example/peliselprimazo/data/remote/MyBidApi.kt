package com.example.peliselprimazo.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Headers

interface MyBidApi {

    @Headers("Accept: application/json")
    @GET("user-spots")
    suspend fun getUserSpots(
        @Query("token") token: String? = null,
        @Header("X-AUTH-TOKEN") authToken: String? = null
    ): MyBidResponse<List<MyBidSpot>>

    @Headers("Accept: application/json")
    @GET("stats")
    suspend fun getStats(
        @Query("token") token: String,
        @Query("date1") date1: String,
        @Query("date2") date2: String,
        @Query("fields") fields: String,
        @Header("X-AUTH-TOKEN") authToken: String = token,
        @Query("orderBy") orderBy: String = "-date",
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("filters") filters: String? = null
    ): MyBidResponse<List<MyBidStat>>

    companion object {
        const val BASE_URL = "https://publishers.mybid.io/backend/api/public/"
    }
}

data class MyBidResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String? = null,
    val message: String? = null // Campo adicional para capturar errores de MyBid
)

data class MyBidSpot(
    val id: String,
    val name: String,
    val adformat: String,
    val status: String,
    val tag_url: String? = null
)

data class MyBidStat(
    val date: String?,
    val impressions: String?,
    val clicks: String?,
    val money: String?,
    val adformat: String?
)
