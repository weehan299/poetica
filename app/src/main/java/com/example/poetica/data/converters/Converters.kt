package com.example.poetica.data.converters

import androidx.room.TypeConverter
import com.example.poetica.data.model.SourceType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromSourceType(value: SourceType): String {
        return value.name
    }

    @TypeConverter
    fun toSourceType(value: String): SourceType {
        return SourceType.valueOf(value)
    }
}