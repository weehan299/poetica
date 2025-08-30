package com.example.poetica.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "poems",
    indices = [
        Index(value = ["title"]),
        Index(value = ["author"]),
        Index(value = ["content"]),
        Index(value = ["title", "author"])
    ]
)
@Serializable
data class Poem(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val content: String,
    val sourceType: SourceType = SourceType.BUNDLED
)

@Serializable
enum class SourceType {
    BUNDLED,
    REMOTE,
    USER_ADDED
}