package com.luciddream.data.db

import androidx.room.TypeConverter
import com.luciddream.model.DreamSign
import com.luciddream.model.DreamTag
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromTagsList(value: List<DreamTag>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toTagsList(value: String): List<DreamTag> {
        return if (value.isBlank()) emptyList() else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromSignsList(value: List<DreamSign>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toSignsList(value: String): List<DreamSign> {
        return if (value.isBlank()) emptyList() else json.decodeFromString(value)
    }
}
