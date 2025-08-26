package com.example.poetica.data.repository

import android.content.Context
import android.util.Log
import com.example.poetica.data.api.ApiConfig
import com.example.poetica.data.api.PoeticaApiService
import com.example.poetica.data.config.PoeticaConfig
import com.example.poetica.data.database.PoemDao
import com.example.poetica.data.mappers.ApiToDomainMapper
import com.example.poetica.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Calendar
import kotlin.random.Random

class PoemRepository(
    private val poemDao: PoemDao,
    val context: Context,
    private val apiService: PoeticaApiService = ApiConfig.createApiService(),
    private val config: PoeticaConfig? = null
) {
    
    companion object {
        private const val TAG = "PoemRepository"
        private const val MAX_CACHE_SIZE = 100
    }
    
    // In-memory cache for API poems to allow reading after search
    private val apiPoemCache = mutableMapOf<String, Poem>()
    
    private fun shouldUseRemoteData(): Boolean {
        val effectiveConfig = config ?: PoeticaConfig.getInstance(context)
        val shouldUse = effectiveConfig.useRemoteData && effectiveConfig.isApiEnabled
        Log.d(TAG, "🔧 shouldUseRemoteData() -> useRemoteData=${effectiveConfig.useRemoteData}, isApiEnabled=${effectiveConfig.isApiEnabled}, result=$shouldUse")
        Log.d(TAG, "🔧 API URL: ${effectiveConfig.apiBaseUrl}")
        return shouldUse
    }
    
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
    
    suspend fun getBrowsePoems(page: Int = 1, pageSize: Int = 20): List<Poem> {
        Log.d(TAG, "📚 getBrowsePoems() called with page=$page, pageSize=$pageSize")
        
        return withContext(Dispatchers.IO) {
            // Try API first if enabled
            if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Attempting API browse poems request...")
                try {
                    val response = apiService.getSections(
                        page = page,
                        size = pageSize,
                        sectionType = "poetry",
                        minWordCount = 10
                    )
                    Log.d(TAG, "🌐 Browse API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body()?.items?.isNotEmpty() == true) {
                        val apiPoems = response.body()!!.items.map { apiSection ->
                            ApiToDomainMapper.mapApiSectionToPoem(apiSection)
                        }
                        // Cache API poems for later retrieval
                        cacheApiPoems(apiPoems)
                        Log.d(TAG, "🌐 ✅ Using API browse results (${apiPoems.size} items)")
                        return@withContext apiPoems
                    } else {
                        Log.w(TAG, "🌐 API browse response not successful or empty")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "🌐 ❌ Failed to get browse poems from API, falling back to local", e)
                }
            } else {
                Log.d(TAG, "🏠 Skipping API browse, using local poems only")
            }
            
            // Fallback to local poems
            Log.d(TAG, "🏠 Getting local poems for browse...")
            val localPoems = poemDao.getAllPoemsSync()
            val startIndex = (page - 1) * pageSize
            val endIndex = minOf(startIndex + pageSize, localPoems.size)
            
            Log.d(TAG, "🏠 Local poems: ${localPoems.size} total, requesting range $startIndex-$endIndex")
            
            if (startIndex >= localPoems.size) {
                Log.d(TAG, "🏠 ✅ Returning empty list (page beyond available poems)")
                emptyList()
            } else {
                val result = localPoems.subList(startIndex, endIndex)
                Log.d(TAG, "🏠 ✅ Returning local browse results (${result.size} items)")
                result
            }
        }
    }
    
    suspend fun getPoemById(id: String): Poem? {
        Log.d(TAG, "📖 getPoemById() called with id='$id'")
        
        return withContext(Dispatchers.IO) {
            // First check local database
            val localPoem = poemDao.getPoemById(id)
            if (localPoem != null) {
                Log.d(TAG, "📖 ✅ Found poem in local database: '${localPoem.title}' by ${localPoem.author}")
                return@withContext localPoem
            }
            
            // If not found locally, check API cache
            val cachedPoem = apiPoemCache[id]
            if (cachedPoem != null) {
                Log.d(TAG, "📖 ✅ Found poem in API cache: '${cachedPoem.title}' by ${cachedPoem.author}")
                return@withContext cachedPoem
            }
            
            Log.d(TAG, "📖 ❌ Poem with id='$id' not found in database or cache")
            null
        }
    }
    
    suspend fun getPoemOfTheDay(): Poem? {
        Log.d(TAG, "🌅 getPoemOfTheDay() called")
        
        return withContext(Dispatchers.IO) {
            // Try API first if enabled
            if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Attempting API poem of the day request...")
                try {
                    val response = apiService.getRandomSections(
                        count = 1,
                        sectionType = "poetry",
                        minWordCount = 20,
                        maxWordCount = 500
                    )
                    Log.d(TAG, "🌐 POTD API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body()?.sections?.isNotEmpty() == true) {
                        val apiSection = response.body()!!.sections.first()
                        val poem = ApiToDomainMapper.mapApiSectionToPoem(apiSection)
                        // Cache API poem for later retrieval
                        cacheApiPoems(listOf(poem))
                        Log.d(TAG, "🌐 ✅ Using API poem of the day: '${poem.title}' by ${poem.author}")
                        return@withContext poem
                    } else {
                        Log.w(TAG, "🌐 API POTD response not successful or empty")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "🌐 ❌ Failed to get poem of the day from API, falling back to local", e)
                }
            } else {
                Log.d(TAG, "🏠 Skipping API POTD, using local selection only")
            }
            
            // Fallback to local poems
            Log.d(TAG, "🏠 Getting local poem of the day...")
            val allPoems = poemDao.getAllPoemsSync()
            if (allPoems.isEmpty()) {
                Log.w(TAG, "🏠 ❌ No local poems available for POTD")
                return@withContext null
            }
            
            // Use current date as seed for consistent daily selection
            val calendar = Calendar.getInstance()
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val year = calendar.get(Calendar.YEAR)
            val seed = (year * 1000L + dayOfYear).toLong()
            val random = Random(seed)
            val selectedIndex = random.nextInt(allPoems.size)
            
            val selectedPoem = allPoems[selectedIndex]
            Log.d(TAG, "🏠 ✅ Selected local POTD: '${selectedPoem.title}' by ${selectedPoem.author} (seed=$seed, index=$selectedIndex)")
            selectedPoem
        }
    }
    
    fun getPoemsByAuthor(author: String): Flow<List<Poem>> = poemDao.getPoemsByAuthor(author)
    
    suspend fun searchPoems(query: String): Flow<List<SearchResult>> = flow {
        Log.d(TAG, "🔍 searchPoems() called with query: '$query'")
        
        if (query.isBlank()) {
            Log.d(TAG, "🔍 Empty query, returning empty results")
            emit(emptyList())
            return@flow
        }
        
        val results = withContext(Dispatchers.IO) {
            // Try API search first if enabled
            if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Attempting API search for: '$query'")
                try {
                    Log.d(TAG, "🌐 Making API call to search endpoint...")
                    val response = apiService.search(
                        query = query.trim(),
                        limit = ApiConfig.DEFAULT_SEARCH_LIMIT,
                        sectionLimit = ApiConfig.DEFAULT_SEARCH_LIMIT
                    )
                    
                    Log.d(TAG, "🌐 API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()!!
                        Log.d(TAG, "🌐 API response body received, parsing...")
                        val apiResults = ApiToDomainMapper.mapApiSearchResponseToSearchResults(responseBody)
                        Log.d(TAG, "🌐 API search results: ${apiResults.size} items found")
                        
                        if (apiResults.isNotEmpty()) {
                            // Cache API poems for later retrieval
                            cacheApiPoems(apiResults.map { it.poem })
                            Log.d(TAG, "🌐 ✅ Using API results (${apiResults.size} items)")
                            return@withContext apiResults
                        } else {
                            Log.d(TAG, "🌐 API returned empty results, falling back to local")
                        }
                    } else {
                        Log.w(TAG, "🌐 API response not successful or empty body: ${response.code()} - ${response.message()}")
                        response.errorBody()?.let {
                            Log.w(TAG, "🌐 Error body: ${it.string()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "🌐 ❌ API search failed for '$query', falling back to local", e)
                }
            } else {
                Log.d(TAG, "🏠 Skipping API search, using local search only")
            }
            
            // Fallback to local search
            Log.d(TAG, "🏠 Performing local search for: '$query'")
            val poems = poemDao.searchPoems(query.trim())
            Log.d(TAG, "🏠 Local search found ${poems.size} poems")
            
            val localResults = poems.map { poem ->
                SearchResult(
                    poem = poem,
                    matchType = determineMatchType(poem, query),
                    relevanceScore = calculateRelevanceScore(poem, query)
                )
            }.sortedByDescending { it.relevanceScore }
            
            Log.d(TAG, "🏠 ✅ Returning local results (${localResults.size} items)")
            localResults
        }
        emit(results)
    }
    
    suspend fun getAllAuthors(): List<String> = poemDao.getAllAuthors()
    
    
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
        
        
        // Content matches get lower score
        if (contentLower.contains(queryLower)) {
            val occurrences = contentLower.split(queryLower).size - 1
            score += occurrences * 10f
        }
        
        return score
    }
    
    // API health and configuration methods
    suspend fun checkApiHealth(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getHealth()
                val isHealthy = response.isSuccessful && response.body()?.status == "healthy"
                if (isHealthy) {
                    Log.d(TAG, "✅ API is healthy")
                }
                isHealthy
            } catch (e: Exception) {
                Log.w(TAG, "API health check failed", e)
                false
            }
        }
    }
    
    fun isRemoteDataEnabled(): Boolean = shouldUseRemoteData()
    
    suspend fun getApiInfo(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getApiInfo()
                if (response.isSuccessful) {
                    val info = response.body()
                    "API: ${info?.message} v${info?.version}"
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get API info", e)
                null
            }
        }
    }
    
    // Cache management methods
    private fun cacheApiPoems(poems: List<Poem>) {
        poems.forEach { poem ->
            apiPoemCache[poem.id] = poem
        }
        
        Log.d(TAG, "💾 Cached ${poems.size} API poems. Cache size: ${apiPoemCache.size}")
        
        // Manage cache size - keep only most recent poems
        if (apiPoemCache.size > MAX_CACHE_SIZE) {
            val keysToRemove = apiPoemCache.keys.take(apiPoemCache.size - MAX_CACHE_SIZE)
            keysToRemove.forEach { apiPoemCache.remove(it) }
            Log.d(TAG, "💾 Cleaned cache, removed ${keysToRemove.size} old entries. New size: ${apiPoemCache.size}")
        }
    }
    
    private fun getCacheStats(): String {
        return "Cache size: ${apiPoemCache.size}/${MAX_CACHE_SIZE}"
    }
    
}