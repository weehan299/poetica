package com.example.poetica.data.mappers

import com.example.poetica.data.api.models.*
import com.example.poetica.data.model.*
import kotlin.random.Random

object ApiToDomainMapper {
    
    fun mapApiSectionToPoem(apiSection: ApiSection): Poem {
        return Poem(
            id = "api_${apiSection.id}",
            title = apiSection.title,
            author = apiSection.work.author.name,
            content = apiSection.contentText,
            sourceType = SourceType.REMOTE
        )
    }
    
    fun mapApiSectionResultToSearchResult(apiSectionResult: ApiSectionResult): SearchResult {
        val poem = Poem(
            id = "api_${apiSectionResult.id}",
            title = apiSectionResult.title,
            author = apiSectionResult.authorName,
            content = apiSectionResult.contentPreview,
            sourceType = SourceType.REMOTE
        )
        
        return SearchResult(
            poem = poem,
            matchType = mapApiMatchType(apiSectionResult.matchType),
            relevanceScore = calculateRelevanceScore(apiSectionResult)
        )
    }
    
    fun mapApiSearchResponseToSearchResults(apiResponse: ApiSearchResponse): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        // Map section results (primary content)
        apiResponse.results.sections.forEach { sectionResult ->
            results.add(mapApiSectionResultToSearchResult(sectionResult))
        }
        
        // TODO: Add work and author results when needed for expanded search features
        
        return results.sortedByDescending { it.relevanceScore }
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
    
    private fun calculateRelevanceScore(sectionResult: ApiSectionResult): Float {
        // Calculate relevance score based on match type and content quality
        val baseScore = when (sectionResult.matchType.lowercase()) {
            "exact" -> 100f
            "title" -> 80f
            "author" -> 60f
            "content" -> 40f
            "fuzzy" -> 30f
            else -> 20f
        }
        
        // Adjust based on word count (prefer reasonably sized poems)
        val wordCountBonus = when {
            sectionResult.wordCount in 20..200 -> 10f
            sectionResult.wordCount in 200..500 -> 5f
            sectionResult.wordCount < 20 -> -5f
            else -> -10f
        }
        
        // Small random factor to vary results
        val randomVariation = Random.nextFloat() * 5f
        
        return baseScore + wordCountBonus + randomVariation
    }
}