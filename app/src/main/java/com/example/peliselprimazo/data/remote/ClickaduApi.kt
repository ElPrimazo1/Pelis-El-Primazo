package com.example.peliselprimazo.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface ClickaduApi {

    @GET("stats")
    suspend fun getStats(
        @Query("token") token: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Query("groupBy") groupBy: String = "geo",
        @Query("format") format: String = "json",
        @Query("zoneId") zoneId: String? = null,
        @Query("adFormat") adFormat: String? = null
    ): ClickaduResponse

    @GET("requestVarStats")
    suspend fun getRequestVarStats(
        @Query("token") token: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Query("format") format: String = "json",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30000
    ): ClickaduRequestVarResponse

    companion object {
        const val BASE_URL = "http://v2.api.clickadu.com/partner/"
    }
}

data class ClickaduResponse(
    val stats: List<ClickaduStat>?,
    val total: ClickaduTotal?,
    val filter: ClickaduFilter?
)

data class ClickaduStat(
    val geo: String?,
    val day: String?,
    val hour: String?,
    val zone: String?,
    val impressions: Long?,
    val money: Double?
)

data class ClickaduTotal(
    val impressions: Long?,
    val money: Double?
)

data class ClickaduFilter(
    val dateFrom: String?,
    val dateTo: String?,
    val groupBy: String?
)

data class ClickaduRequestVarResponse(
    val stats: List<ClickaduRequestVarItem>?,
    val total: ClickaduRequestVarTotal?
)

data class ClickaduRequestVarItem(
    val request_var: String?,
    val money: Double?
)

data class ClickaduRequestVarTotal(
    val money: Double?
)
