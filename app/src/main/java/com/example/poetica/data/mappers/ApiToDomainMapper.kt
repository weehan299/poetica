package com.example.poetica.data.mappers

import com.example.poetica.data.api.models.*
import com.example.poetica.data.model.*
import kotlin.random.Random

object ApiToDomainMapper {
    
    fun mapApiPoemToPoem(apiPoem: ApiPoem): Poem {
        return Poem(
            id = apiPoem.canonicalId,
            title = apiPoem.title,
            author = apiPoem.author.name,
            content = apiPoem.text,
            sourceType = SourceType.REMOTE
        )
    }
    
    fun mapApiPoemListItemToPoem(apiPoemListItem: ApiPoemListItem): Poem {
        return Poem(
            id = apiPoemListItem.canonicalId,
            title = apiPoemListItem.title,
            author = apiPoemListItem.author.name,
            content = apiPoemListItem.firstLine, // Note: Only first line available in list view
            sourceType = SourceType.REMOTE
        )
    }
    
    fun mapApiPoemSearchResultToSearchResult(apiPoemSearchResult: ApiPoemSearchResult): SearchResult {
        // Note: Using contentPreview for search results (truncated for performance)
        // Full content will be fetched on-demand when user clicks to read the poem
        val poem = Poem(
            id = apiPoemSearchResult.canonicalId,
            title = apiPoemSearchResult.title,
            author = apiPoemSearchResult.authorName,
            content = apiPoemSearchResult.contentPreview,
            sourceType = SourceType.REMOTE
        )
        
        return SearchResult(
            poem = poem,
            matchType = mapApiMatchType(apiPoemSearchResult.matchType),
            relevanceScore = calculateRelevanceScore(apiPoemSearchResult)
        )
    }
    
    fun mapApiSearchResponseToSearchResults(apiResponse: ApiSearchResponse): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        // Map poem results (primary content)
        apiResponse.results.poems.forEach { poemResult ->
            results.add(mapApiPoemSearchResultToSearchResult(poemResult))
        }
        
        // TODO: Add author results when needed for expanded search features
        
        return results.sortedByDescending { it.relevanceScore }
    }
    
    fun mapApiSearchResponseToSearchResultItems(apiResponse: ApiSearchResponse): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        
        // Map author results first (higher priority)
        apiResponse.results.authors.forEach { authorResult ->
            val authorSearchResult = AuthorSearchResult(
                author = Author(
                    name = authorResult.name,
                    poemCount = authorResult.poemCount
                ),
                matchType = mapApiMatchType(authorResult.matchType),
                relevanceScore = calculateAuthorRelevanceScore(authorResult)
            )
            results.add(SearchResultItem.AuthorResult(authorSearchResult))
        }
        
        // Map poem results
        apiResponse.results.poems.forEach { poemResult ->
            val searchResult = mapApiPoemSearchResultToSearchResult(poemResult)
            results.add(SearchResultItem.PoemResult(searchResult))
        }
        
        return results.sortedByDescending { item ->
            when (item) {
                is SearchResultItem.AuthorResult -> item.authorSearchResult.relevanceScore
                is SearchResultItem.PoemResult -> item.searchResult.relevanceScore
            }
        }
    }
    
    
    private fun mapApiMatchType(apiMatchType: String): MatchType {
        return when (apiMatchType.lowercase()) {
            "title" -> MatchType.TITLE_PARTIAL
            "exact" -> MatchType.TITLE_EXACT
            "content" -> MatchType.CONTENT
            "author" -> MatchType.AUTHOR_PARTIAL
            "fuzzy" -> MatchType.AUTHOR_PARTIAL
            else -> MatchType.CONTENT
        }
    }
    
    private fun calculateRelevanceScore(poemResult: ApiPoemSearchResult): Float {
        // Calculate relevance score based on match type
        val baseScore = when (poemResult.matchType.lowercase()) {
            "exact" -> 100f
            "title" -> 80f
            "author" -> 60f
            "content" -> 40f
            "fuzzy" -> 30f
            else -> 20f
        }
        
        // Adjust based on content preview length (prefer reasonably sized poems)
        val contentLength = poemResult.contentPreview.length
        val lengthBonus = when {
            contentLength in 100..500 -> 10f
            contentLength in 500..1000 -> 5f
            contentLength < 100 -> -5f
            else -> -10f
        }
        
        // Small random factor to vary results
        val randomVariation = Random.nextFloat() * 5f
        
        return baseScore + lengthBonus + randomVariation
    }
    
    private fun calculateAuthorRelevanceScore(authorResult: ApiAuthorResult): Float {
        // Calculate relevance score based on match type
        val baseScore = when (authorResult.matchType.lowercase()) {
            "exact" -> 150f  // Authors with exact matches get highest priority
            "fuzzy" -> 120f
            "partial" -> 100f
            else -> 80f
        }
        
        // Bonus for authors with more poems (popular authors)
        val poemCountBonus = when {
            authorResult.poemCount >= 100 -> 20f
            authorResult.poemCount >= 50 -> 15f
            authorResult.poemCount >= 20 -> 10f
            authorResult.poemCount >= 10 -> 5f
            else -> 0f
        }
        
        // Small random factor to vary results
        val randomVariation = Random.nextFloat() * 3f
        
        return baseScore + poemCountBonus + randomVariation
    }
}