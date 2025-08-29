package com.example.poetica.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiPoem(
    @SerialName("canonical_id") val canonicalId: String,
    val title: String,
    @SerialName("first_line") val firstLine: String,
    val language: String,
    val author: ApiAuthor,
    val text: String,
    @SerialName("work_title") val workTitle: String? = null,
    @SerialName("source_origin") val sourceOrigin: String,
    @SerialName("source_work_id") val sourceWorkId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ApiPoemListItem(
    @SerialName("canonical_id") val canonicalId: String,
    val title: String,
    @SerialName("first_line") val firstLine: String,
    val language: String,
    val author: ApiAuthor
)

@Serializable
data class ApiAuthor(
    val id: Int,
    val name: String
)

@Serializable
data class ApiSearchResponse(
    val query: String,
    val results: ApiSearchResults,
    @SerialName("total_results") val totalResults: Int,
    @SerialName("search_time_ms") val searchTimeMs: Float
)

@Serializable
data class ApiSearchResults(
    val authors: List<ApiAuthorResult> = emptyList(),
    val poems: List<ApiPoemSearchResult> = emptyList()
)

@Serializable
data class ApiAuthorResult(
    val id: Int,
    val name: String,
    @SerialName("match_type") val matchType: String,
    @SerialName("poem_count") val poemCount: Int
)

@Serializable
data class ApiPoemSearchResult(
    @SerialName("canonical_id") val canonicalId: String,
    val title: String,
    @SerialName("content_preview") val contentPreview: String,
    @SerialName("first_line") val firstLine: String,
    @SerialName("work_title") val workTitle: String? = null,
    @SerialName("author_name") val authorName: String,
    val language: String,
    @SerialName("match_type") val matchType: String
)



@Serializable
data class ApiPoemsResponse(
    val items: List<ApiPoemListItem>,
    val total: Int,
    val page: Int,
    val size: Int,
    val pages: Int,
    @SerialName("has_next") val hasNext: Boolean,
    @SerialName("has_prev") val hasPrev: Boolean
)

@Serializable
data class ApiHealthResponse(
    val status: String,
    val version: String
)

@Serializable
data class ApiInfoResponse(
    val message: String,
    val version: String,
    val docs: String,
    val health: String
)

@Serializable
data class ApiPoemStatsResponse(
    @SerialName("total_poems") val totalPoems: Int,
    @SerialName("poems_by_language") val poemsByLanguage: Map<String, Int>,
    @SerialName("poems_by_source") val poemsBySource: Map<String, Int>
)

@Serializable
data class ApiAuthorStatsResponse(
    @SerialName("total_authors") val totalAuthors: Int,
    @SerialName("authors_with_poems") val authorsWithPoems: Int,
    @SerialName("authors_without_poems") val authorsWithoutPoems: Int,
    @SerialName("top_authors_by_poems") val topAuthorsByPoems: List<ApiTopAuthor>
)

@Serializable
data class ApiTopAuthor(
    val name: String,
    @SerialName("poem_count") val poemCount: Int
)

@Serializable
data class ApiAuthorsResponse(
    val items: List<ApiAuthor>,
    val total: Int,
    val page: Int,
    val size: Int,
    val pages: Int,
    @SerialName("has_next") val hasNext: Boolean,
    @SerialName("has_prev") val hasPrev: Boolean
)

@Serializable
data class ApiAuthorSearchResponse(
    val query: String,
    val results: List<ApiAuthorResult>,
    @SerialName("total_results") val totalResults: Int
)

@Serializable
data class ApiPoemSearchResponse(
    val query: String,
    val results: List<ApiPoemSearchResult>,
    @SerialName("total_results") val totalResults: Int
)