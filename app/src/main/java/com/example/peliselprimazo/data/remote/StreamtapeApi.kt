package com.example.peliselprimazo.data.remote

import com.example.peliselprimazo.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface StreamtapeApi {

    @GET("account/info")
    suspend fun getAccountInfo(
        @Query("login") login: String,
        @Query("key") key: String
    ): StreamtapeResponse<Any>

    @GET("file/listfolder")
    suspend fun listFolder(
        @Query("login") login: String,
        @Query("key") key: String,
        @Query("folder") folderId: String? = null
    ): StreamtapeResponse<StreamtapeFolderList>

    @GET("file/dlticket")
    suspend fun getDownloadTicket(
        @Query("file") fileId: String,
        @Query("login") login: String,
        @Query("key") key: String
    ): StreamtapeResponse<StreamtapeTicket>

    @GET("file/dl")
    suspend fun getDownloadLink(
        @Query("file") fileId: String,
        @Query("ticket") ticket: String
    ): StreamtapeResponse<StreamtapeDownloadLink>
    
    companion object {
        const val BASE_URL = "https://api.streamtape.com/"
    }
}
