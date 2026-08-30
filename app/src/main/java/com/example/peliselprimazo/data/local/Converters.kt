package com.example.peliselprimazo.data.local

import androidx.room.TypeConverter
import com.example.peliselprimazo.domain.model.CastMember
import com.example.peliselprimazo.domain.model.ServerLink
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromServerLinkList(value: List<ServerLink>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toServerLinkList(value: String): List<ServerLink> {
        val listType = object : TypeToken<List<ServerLink>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromCastList(value: List<CastMember>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toCastList(value: String): List<CastMember> {
        val listType = object : TypeToken<List<CastMember>>() {}.type
        return Gson().fromJson(value, listType)
    }
}
