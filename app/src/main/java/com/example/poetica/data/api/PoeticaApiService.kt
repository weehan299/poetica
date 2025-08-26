package com.example.poetica.data.api

import com.example.poetica.data.api.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PoeticaApiService {
    
    @GET("/")
    suspend fun getApiInfo(): Response<ApiInfoResponse>
    
    @GET("/api/health")
    suspend fun getHealth(): Response<ApiHealthResponse>
    
    // Search endpoints
    @GET("/api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
        @Query("author_limit") authorLimit: Int? = null,
        @Query("work_limit") workLimit: Int? = null,
        @Query("section_limit") sectionLimit: Int? = null
    ): Response<ApiSearchResponse>
    
    @GET("/api/search/sections")
    suspend fun searchSections(
        @Query("q") query: String,
        @Query("limit") limit: Int? = 30,
        @Query("section_type") sectionType: String? = "poetry"
    ): Response<ApiSectionResult>
    
    // Section endpoints
    @GET("/api/sections")
    suspend fun getSections(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "section_order",
        @Query("order") order: String = "asc",
        @Query("title") title: String? = null,
        @Query("section_type") sectionType: String? = "poetry",
        @Query("work_id") workId: Int? = null,
        @Query("author_id") authorId: Int? = null,
        @Query("min_word_count") minWordCount: Int? = null,
        @Query("max_word_count") maxWordCount: Int? = null
    ): Response<ApiSectionsResponse>
    
    @GET("/api/sections/{section_id}")
    suspend fun getSection(
        @Path("section_id") sectionId: Int
    ): Response<ApiSection>
    
    @GET("/api/sections/random")
    suspend fun getRandomSections(
        @Query("count") count: Int = 1,
        @Query("section_type") sectionType: String = "poetry",
        @Query("min_word_count") minWordCount: Int = 10,
        @Query("max_word_count") maxWordCount: Int? = null
    ): Response<ApiRandomSectionsResponse>
    
    // Author endpoints (for future use)
    @GET("/api/authors")
    suspend fun getAuthors(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "name",
        @Query("order") order: String = "asc",
        @Query("name") name: String? = null
    ): Response<Any> // TODO: Define ApiAuthorsResponse when needed
    
    @GET("/api/authors/{author_id}")
    suspend fun getAuthor(
        @Path("author_id") authorId: Int
    ): Response<ApiAuthor>
    
    // Work endpoints (for future use)
    @GET("/api/works")
    suspend fun getWorks(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "title",
        @Query("order") order: String = "asc",
        @Query("title") title: String? = null,
        @Query("language") language: String? = null,
        @Query("work_type") workType: String? = null,
        @Query("author_id") authorId: Int? = null
    ): Response<Any> // TODO: Define ApiWorksResponse when needed
    
    @GET("/api/works/{work_id}")
    suspend fun getWork(
        @Path("work_id") workId: Int
    ): Response<ApiWork>
}