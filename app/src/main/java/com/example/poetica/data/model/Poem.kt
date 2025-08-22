package com.example.poetica.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "poems")
@Serializable
data class Poem(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val content: String,
    val year: Int? = null,
    val tags: List<String> = emptyList(),
    val sourceType: SourceType = SourceType.BUNDLED,
    val language: String = "en",
    val summary: String? = null
)

@Serializable
enum class SourceType {
    BUNDLED,
    REMOTE,
    USER_ADDED
}

data class PoemWithSearchData(
    val poem: Poem,
    val searchableText: String
)