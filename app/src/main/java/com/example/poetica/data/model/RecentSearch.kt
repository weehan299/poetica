package com.example.poetica.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_searches",
    indices = [
        Index(value = ["searchDate"]),
        Index(value = ["query"])
    ]
)
data class RecentSearch(
    @PrimaryKey val id: String,
    val query: String,
    val searchDate: Long,
    val resultCount: Int
)