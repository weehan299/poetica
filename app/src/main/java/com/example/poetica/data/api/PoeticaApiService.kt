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
        @Query("poem_limit") poemLimit: Int? = null
    ): Response<ApiSearchResponse>
    
    @GET("/api/search/authors")
    suspend fun searchAuthors(
        @Query("q") query: String,
        @Query("limit") limit: Int? = 20
    ): Response<ApiAuthorSearchResponse>
    
    @GET("/api/search/poems")
    suspend fun searchPoems(
        @Query("q") query: String,
        @Query("limit") limit: Int? = 30
    ): Response<ApiPoemSearchResponse>
    
    // Poem endpoints
    @GET("/api/poems")
    suspend fun getPoems(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "title",
        @Query("order") order: String = "asc",
        @Query("title") title: String? = null,
        @Query("author_name") authorName: String? = null,
        @Query("language") language: String? = null,
        @Query("source_origin") sourceOrigin: String? = null
    ): Response<ApiPoemsResponse>
    
    @GET("/api/poems/{canonical_id}")
    suspend fun getPoem(
        @Path("canonical_id") canonicalId: String
    ): Response<ApiPoem>
    
    @GET("/api/poems/random")
    suspend fun getRandomPoem(
        @Query("language") language: String = "en"
    ): Response<ApiPoem>
    
    @GET("/api/poems/stats")
    suspend fun getPoemStats(): Response<ApiPoemStatsResponse>
    
    // Author endpoints
    @GET("/api/authors")
    suspend fun getAuthors(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "name",
        @Query("order") order: String = "asc",
        @Query("name") name: String? = null
    ): Response<ApiAuthorsResponse>
    
    @GET("/api/authors/{author_id}")
    suspend fun getAuthor(
        @Path("author_id") authorId: Int
    ): Response<ApiAuthor>
    
    @GET("/api/authors/{author_id}/poems")
    suspend fun getAuthorPoems(
        @Path("author_id") authorId: Int,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<ApiPoemsResponse>
    
    @GET("/api/authors/stats")
    suspend fun getAuthorStats(): Response<ApiAuthorStatsResponse>
    
}