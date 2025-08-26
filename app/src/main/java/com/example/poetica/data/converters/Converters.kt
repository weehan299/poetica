package com.example.poetica.data.converters

import androidx.room.TypeConverter
import com.example.poetica.data.model.SourceType

class Converters {
    
    @TypeConverter
    fun fromSourceType(value: SourceType): String {
        return value.name
    }

    @TypeConverter
    fun toSourceType(value: String): SourceType {
        return SourceType.valueOf(value)
    }
}