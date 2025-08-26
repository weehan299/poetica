package com.example.poetica.ui.search

import android.util.Log
import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.SearchResult
import com.example.poetica.data.model.MatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SearchEngine provides local indexing and search capabilities.
 * Note: Primary search is now handled by PoemRepository with API integration.
 * This engine is used for suggestions and offline scenarios.
 */

class SearchEngine {
    
    companion object {
        private const val TAG = "SearchEngine"
    }
    
    data class SearchIndex(
        val poems: List<Poem>,
        val titleWords: Map<String, List<String>>, // word -> poem IDs
        val authorWords: Map<String, List<String>>,
        val contentWords: Map<String, List<String>>
    )
    
    private var searchIndex: SearchIndex? = null
    
    suspend fun buildIndex(poems: List<Poem>) {
        Log.d(TAG, "📚 buildIndex() called with ${poems.size} poems")
        
        withContext(Dispatchers.Default) {
            val titleWords = mutableMapOf<String, MutableList<String>>()
            val authorWords = mutableMapOf<String, MutableList<String>>()
            val contentWords = mutableMapOf<String, MutableList<String>>()
            
            poems.forEachIndexed { index, poem ->
                if (index == 0 || index % 10 == 0 || index == poems.size - 1) {
                    Log.d(TAG, "📚 Indexing poem ${index + 1}/${poems.size}: '${poem.title}' by ${poem.author}")
                }
                
                // Index title words
                val titleWordsCount = poem.title.lowercase().split(Regex("\\W+"))
                    .filter { it.isNotBlank() }
                    .also { words ->
                        words.forEach { word ->
                            titleWords.getOrPut(word) { mutableListOf() }.add(poem.id)
                        }
                    }.size
                
                // Index author words
                val authorWordsCount = poem.author.lowercase().split(Regex("\\W+"))
                    .filter { it.isNotBlank() }
                    .also { words ->
                        words.forEach { word ->
                            authorWords.getOrPut(word) { mutableListOf() }.add(poem.id)
                        }
                    }.size
                
                // Index content words
                val contentWordsCount = poem.content.lowercase().split(Regex("\\W+"))
                    .filter { it.isNotBlank() && it.length > 2 } // Skip very short words
                    .also { words ->
                        words.forEach { word ->
                            contentWords.getOrPut(word) { mutableListOf() }.add(poem.id)
                        }
                    }.size
                
                if (index < 3) { // Log details for first few poems
                    Log.d(TAG, "📚   Title words: $titleWordsCount, Author words: $authorWordsCount, Content words: $contentWordsCount")
                }
            }
            
            searchIndex = SearchIndex(
                poems = poems,
                titleWords = titleWords,
                authorWords = authorWords,
                contentWords = contentWords
            )
            
            Log.d(TAG, "✅ Index built successfully:")
            Log.d(TAG, "✅   Total poems: ${poems.size}")
            Log.d(TAG, "✅   Title words: ${titleWords.size}")
            Log.d(TAG, "✅   Author words: ${authorWords.size}")
            Log.d(TAG, "✅   Content words: ${contentWords.size}")
        }
    }
    
    suspend fun search(query: String): List<SearchResult> {
        Log.d(TAG, "🔍 search() called with query: '$query'")
        
        return withContext(Dispatchers.Default) {
            val index = searchIndex ?: run {
                Log.w(TAG, "❌ No search index available, returning empty results")
                return@withContext emptyList()
            }
            
            if (query.isBlank()) {
                Log.d(TAG, "🔍 Empty query, returning empty results")
                return@withContext emptyList()
            }
            
            val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
            Log.d(TAG, "🔍 Query words: $queryWords")
            
            val results = mutableMapOf<String, SearchResult>()
            
            queryWords.forEachIndexed { wordIndex, word ->
                Log.d(TAG, "🔍 Searching for word ${wordIndex + 1}/${queryWords.size}: '$word'")
                
                // Search in titles
                val titleMatches = index.titleWords[word]?.size ?: 0
                index.titleWords[word]?.forEach { poemId ->
                    val poem = index.poems.find { it.id == poemId }
                    poem?.let {
                        val existing = results[poemId]
                        val score = (existing?.relevanceScore ?: 0f) + 50f
                        results[poemId] = SearchResult(
                            poem = it,
                            matchType = if (existing?.matchType == MatchType.TITLE_EXACT) MatchType.TITLE_EXACT else MatchType.TITLE_PARTIAL,
                            relevanceScore = score
                        )
                    }
                }
                
                // Search in authors
                val authorMatches = index.authorWords[word]?.size ?: 0
                index.authorWords[word]?.forEach { poemId ->
                    val poem = index.poems.find { it.id == poemId }
                    poem?.let {
                        val existing = results[poemId]
                        val score = (existing?.relevanceScore ?: 0f) + 40f
                        results[poemId] = SearchResult(
                            poem = it,
                            matchType = if (existing == null) MatchType.AUTHOR_PARTIAL else existing.matchType,
                            relevanceScore = score
                        )
                    }
                }
                
                
                // Search in content
                val contentMatches = index.contentWords[word]?.size ?: 0
                index.contentWords[word]?.forEach { poemId ->
                    val poem = index.poems.find { it.id == poemId }
                    poem?.let {
                        val existing = results[poemId]
                        val score = (existing?.relevanceScore ?: 0f) + 10f
                        results[poemId] = SearchResult(
                            poem = it,
                            matchType = if (existing == null) MatchType.CONTENT else existing.matchType,
                            relevanceScore = score
                        )
                    }
                }
                
                Log.d(TAG, "🔍 Word '$word' matches: title=$titleMatches, author=$authorMatches, content=$contentMatches")
            }
            
            val finalResults = results.values.sortedByDescending { it.relevanceScore }
            Log.d(TAG, "✅ Local search completed: ${finalResults.size} results found")
            if (finalResults.isNotEmpty()) {
                Log.d(TAG, "✅ Top 3 results: ${finalResults.take(3).map { "'${it.poem.title}' (${it.relevanceScore})" }}")
            }
            
            finalResults
        }
    }
    
    fun getSuggestions(query: String): List<String> {
        Log.d(TAG, "💡 getSuggestions() called with query: '$query'")
        
        val index = searchIndex ?: run {
            Log.w(TAG, "❌ No search index available for suggestions")
            return emptyList()
        }
        
        if (query.isBlank()) {
            Log.d(TAG, "💡 Empty query, returning empty suggestions")
            return emptyList()
        }
        
        val queryLower = query.lowercase()
        val suggestions = mutableSetOf<String>()
        
        // Add author suggestions
        index.poems.map { it.author }
            .distinct()
            .filter { it.lowercase().contains(queryLower) }
            .take(3)
            .forEach { suggestions.add(it) }
        
        // Add title suggestions
        index.poems.map { it.title }
            .distinct()
            .filter { it.lowercase().contains(queryLower) }
            .take(3)
            .forEach { suggestions.add(it) }
        
        
        val finalSuggestions = suggestions.take(8).toList()
        Log.d(TAG, "💡 Generated ${finalSuggestions.size} suggestions: $finalSuggestions")
        
        return finalSuggestions
    }
}