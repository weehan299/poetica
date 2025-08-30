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
    
    fun mapApiAuthorResultToAuthor(apiAuthorResult: ApiAuthorResult): Author {
        return Author(
            name = apiAuthorResult.name,
            poemCount = apiAuthorResult.poemCount
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
        
        return results.sortedByDescending { it.relevanceScore }
    }
    
    fun mapApiSearchResponseToSearchResponse(apiResponse: ApiSearchResponse): SearchResponse {
        // Map authors from API response
        val authors = apiResponse.results.authors.map { authorResult ->
            mapApiAuthorResultToAuthor(authorResult)
        }
        
        // Map poem results
        val poems = apiResponse.results.poems.map { poemResult ->
            mapApiPoemSearchResultToSearchResult(poemResult)
        }.sortedByDescending { it.relevanceScore }
        
        return SearchResponse(
            authors = authors,
            poems = poems,
            query = apiResponse.query
        )
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
}