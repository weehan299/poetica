package com.example.poetica.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiSection(
    val id: Int,
    val title: String,
    @SerialName("content_text") val contentText: String,
    @SerialName("content_html") val contentHtml: String? = null,
    @SerialName("section_order") val sectionOrder: Int,
    @SerialName("parent_section_order") val parentSectionOrder: Int? = null,
    @SerialName("parent_section_id") val parentSectionId: Int? = null,
    val depth: Int = 0,
    @SerialName("html_file") val htmlFile: String? = null,
    @SerialName("anchor_id") val anchorId: String? = null,
    @SerialName("section_type") val sectionType: String,
    @SerialName("word_count") val wordCount: Int,
    val lang: String? = "en",
    val work: ApiWork,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ApiWork(
    val id: Int,
    val title: String,
    val language: String = "en",
    @SerialName("work_type") val workType: String,
    val slug: String,
    val author: ApiAuthor
)

@Serializable
data class ApiAuthor(
    val id: Int,
    val name: String,
    val slug: String
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
    val works: List<ApiWorkResult> = emptyList(),
    val sections: List<ApiSectionResult> = emptyList()
)

@Serializable
data class ApiAuthorResult(
    val id: Int,
    val name: String,
    val slug: String,
    @SerialName("match_type") val matchType: String,
    @SerialName("work_count") val workCount: Int
)

@Serializable
data class ApiWorkResult(
    val id: Int,
    val title: String,
    val slug: String,
    val language: String,
    @SerialName("work_type") val workType: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_slug") val authorSlug: String,
    @SerialName("match_type") val matchType: String,
    @SerialName("section_count") val sectionCount: Int
)

@Serializable
data class ApiSectionResult(
    val id: Int,
    val title: String,
    @SerialName("content_preview") val contentPreview: String,
    @SerialName("section_order") val sectionOrder: Int,
    @SerialName("section_type") val sectionType: String,
    @SerialName("word_count") val wordCount: Int,
    @SerialName("work_title") val workTitle: String,
    @SerialName("work_slug") val workSlug: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_slug") val authorSlug: String,
    @SerialName("match_type") val matchType: String
)

@Serializable
data class ApiRandomSectionsResponse(
    val sections: List<ApiSection>,
    val count: Int,
    @SerialName("filtered_by") val filteredBy: Map<String, String?>
)

@Serializable
data class ApiSectionsResponse(
    val items: List<ApiSection>,
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