package com.example.poetica.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "author_poem_remote_keys")
data class AuthorPoemRemoteKeys(
    @PrimaryKey
    val author: String,
    val prevKey: Int?,
    val nextKey: Int?
)