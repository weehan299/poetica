package com.example.poetica.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PoemCollection(
    val name: String,
    val description: String?,
    val poems: List<Poem>,
    val tags: List<String> = emptyList(),
    val curator: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class BundledPoems(
    val collections: List<PoemCollection>
)

data class SearchResult(
    val poem: Poem,
    val matchType: MatchType,
    val matchedText: String? = null,
    val relevanceScore: Float = 0f
)

enum class MatchType {
    TITLE_EXACT,
    TITLE_PARTIAL,
    AUTHOR_EXACT,
    AUTHOR_PARTIAL,
    CONTENT,
    TAG
}