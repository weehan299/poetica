package com.example.poetica.data.repository

import android.content.Context
import com.example.poetica.data.database.PoemDao
import com.example.poetica.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

class PoemRepository(
    private val poemDao: PoemDao,
    private val context: Context
) {
    
    suspend fun initializeWithBundledPoems() {
        withContext(Dispatchers.IO) {
            val count = poemDao.getPoemCount()
            if (count == 0) {
                loadBundledPoems()
            }
        }
    }
    
    private suspend fun loadBundledPoems() {
        try {
            val jsonString = context.assets.open("poems.json").bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true }
            val bundledPoems = json.decodeFromString<BundledPoems>(jsonString)
            val allPoems = bundledPoems.collections.flatMap { it.poems }
            poemDao.insertPoems(allPoems)
        } catch (e: IOException) {
            throw Exception("Failed to read poems.json from assets: ${e.message}", e)
        } catch (e: Exception) {
            throw Exception("Failed to parse poems JSON: ${e.message}", e)
        }
    }
    
    fun getAllPoems(): Flow<List<Poem>> = poemDao.getAllPoems()
    
    suspend fun getPoemById(id: String): Poem? = poemDao.getPoemById(id)
    
    fun getPoemsByAuthor(author: String): Flow<List<Poem>> = poemDao.getPoemsByAuthor(author)
    
    suspend fun searchPoems(query: String): Flow<List<SearchResult>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        
        val results = withContext(Dispatchers.IO) {
            val poems = poemDao.searchPoems(query.trim())
            poems.map { poem ->
                SearchResult(
                    poem = poem,
                    matchType = determineMatchType(poem, query),
                    relevanceScore = calculateRelevanceScore(poem, query)
                )
            }.sortedByDescending { it.relevanceScore }
        }
        
        emit(results)
    }
    
    suspend fun getAllAuthors(): List<String> = poemDao.getAllAuthors()
    
    suspend fun getAllTags(): List<String> {
        val rawTags = poemDao.getAllTagsRaw()
        return rawTags.flatMap { tagListString ->
            try {
                Json.decodeFromString<List<String>>(tagListString)
            } catch (e: Exception) {
                emptyList()
            }
        }.distinct().sorted()
    }
    
    suspend fun insertPoem(poem: Poem) = poemDao.insertPoem(poem)
    
    suspend fun updatePoem(poem: Poem) = poemDao.updatePoem(poem)
    
    suspend fun deletePoem(poem: Poem) = poemDao.deletePoem(poem)
    
    private fun determineMatchType(poem: Poem, query: String): MatchType {
        val queryLower = query.lowercase()
        val titleLower = poem.title.lowercase()
        val authorLower = poem.author.lowercase()
        
        return when {
            titleLower == queryLower -> MatchType.TITLE_EXACT
            authorLower == queryLower -> MatchType.AUTHOR_EXACT
            titleLower.contains(queryLower) -> MatchType.TITLE_PARTIAL
            authorLower.contains(queryLower) -> MatchType.AUTHOR_PARTIAL
            poem.tags.any { it.lowercase().contains(queryLower) } -> MatchType.TAG
            else -> MatchType.CONTENT
        }
    }
    
    private fun calculateRelevanceScore(poem: Poem, query: String): Float {
        val queryLower = query.lowercase()
        val titleLower = poem.title.lowercase()
        val authorLower = poem.author.lowercase()
        val contentLower = poem.content.lowercase()
        
        var score = 0f
        
        // Title matches get highest score
        when {
            titleLower == queryLower -> score += 100f
            titleLower.startsWith(queryLower) -> score += 80f
            titleLower.contains(queryLower) -> score += 60f
        }
        
        // Author matches get high score
        when {
            authorLower == queryLower -> score += 90f
            authorLower.startsWith(queryLower) -> score += 70f
            authorLower.contains(queryLower) -> score += 50f
        }
        
        // Tag matches
        poem.tags.forEach { tag ->
            val tagLower = tag.lowercase()
            when {
                tagLower == queryLower -> score += 40f
                tagLower.contains(queryLower) -> score += 20f
            }
        }
        
        // Content matches get lower score
        if (contentLower.contains(queryLower)) {
            val occurrences = contentLower.split(queryLower).size - 1
            score += occurrences * 10f
        }
        
        return score
    }
}