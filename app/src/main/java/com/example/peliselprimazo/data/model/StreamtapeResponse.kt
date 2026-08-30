package com.example.peliselprimazo.data.model

import com.google.gson.annotations.SerializedName

data class StreamtapeResponse<T>(
    @SerializedName("status") val status: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("result") val result: T?
)

data class StreamtapeFolderList(
    @SerializedName("folders") val folders: List<StreamtapeFolder>? = emptyList(),
    @SerializedName("files") val files: List<StreamtapeFile>? = emptyList()
)

data class StreamtapeFolder(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

data class StreamtapeFile(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("link") val link: String,
    @SerializedName("linkid") val linkid: String,
    @SerializedName("created_at") val createdAt: Long? = null,
    @SerializedName("downloads") val downloads: Int? = null,
    @SerializedName("convert") val convert: String? = null
)

data class StreamtapeTicket(
    @SerializedName("ticket") val ticket: String,
    @SerializedName("wait_time") val waitTime: Int,
    @SerializedName("valid_until") val validUntil: String
)

data class StreamtapeDownloadLink(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("url") val url: String
)