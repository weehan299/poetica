package com.example.poetica.ui.search

import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.SearchResult
import com.example.poetica.data.model.MatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchEngine {
    
    data class SearchIndex(
        val poems: List<Poem>,
        val titleWords: Map<String, List<String>>, // word -> poem IDs
        val authorWords: Map<String, List<String>>,
        val contentWords: Map<String, List<String>>,
        val tags: Map<String, List<String>>
    )
    
    private var searchIndex: SearchIndex? = null
    
    suspend fun buildIndex(poems: List<Poem>) {
        withContext(Dispatchers.Default) {
            val titleWords = mutableMapOf<String, MutableList<String>>()
            val authorWords = mutableMapOf<String, MutableList<String>>()
            val contentWords = mutableMapOf<String, MutableList<String>>()
            val tags = mutableMapOf<String, MutableList<String>>()
            
            poems.forEach { poem ->
                // Index title words
                poem.title.lowercase().split(Regex("\\W+"))
                    .filter { it.isNotBlank() }
                    .forEach { word ->
                        titleWords.getOrPut(word) { mutableListOf() }.add(poem.id)
                    }
                
                // Index author words
                poem.author.lowercase().split(Regex("\\W+"))
                    .filter { it.isNotBlank() }
                    .forEach { word ->
                        authorWords.getOrPut(word) { mutableListOf() }.add(poem.id)
                    }
                
                // Index content words
                poem.content.lowercase().split(Regex("\\W+"))
                    .filter { it.isNotBlank() && it.length > 2 } // Skip very short words
                    .forEach { word ->
                        contentWords.getOrPut(word) { mutableListOf() }.add(poem.id)
                    }
                
                // Index tags
                poem.tags.forEach { tag ->
                    tag.lowercase().split(Regex("\\W+"))
                        .filter { it.isNotBlank() }
                        .forEach { word ->
                            tags.getOrPut(word) { mutableListOf() }.add(poem.id)
                        }
                }
            }
            
            searchIndex = SearchIndex(
                poems = poems,
                titleWords = titleWords,
                authorWords = authorWords,
                contentWords = contentWords,
                tags = tags
            )
        }
    }
    
    suspend fun search(query: String): List<SearchResult> {
        return withContext(Dispatchers.Default) {
            val index = searchIndex ?: return@withContext emptyList()
            if (query.isBlank()) return@withContext emptyList()
            
            val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
            val results = mutableMapOf<String, SearchResult>()
            
            queryWords.forEach { word ->
                // Search in titles
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
                
                // Search in tags
                index.tags[word]?.forEach { poemId ->
                    val poem = index.poems.find { it.id == poemId }
                    poem?.let {
                        val existing = results[poemId]
                        val score = (existing?.relevanceScore ?: 0f) + 30f
                        results[poemId] = SearchResult(
                            poem = it,
                            matchType = if (existing == null) MatchType.TAG else existing.matchType,
                            relevanceScore = score
                        )
                    }
                }
                
                // Search in content
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
            }
            
            results.values.sortedByDescending { it.relevanceScore }
        }
    }
    
    fun getSuggestions(query: String): List<String> {
        val index = searchIndex ?: return emptyList()
        if (query.isBlank()) return emptyList()
        
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
        
        // Add tag suggestions
        index.poems.flatMap { it.tags }
            .distinct()
            .filter { it.lowercase().contains(queryLower) }
            .take(2)
            .forEach { suggestions.add(it) }
        
        return suggestions.take(8).toList()
    }
}