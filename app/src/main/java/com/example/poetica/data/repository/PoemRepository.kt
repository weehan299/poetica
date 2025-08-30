package com.example.poetica.data.repository

import android.content.Context
import android.util.Log
import com.example.poetica.data.api.ApiConfig
import com.example.poetica.data.api.PoeticaApiService
import com.example.poetica.data.config.PoeticaConfig
import com.example.poetica.data.database.PoemDao
import com.example.poetica.data.database.AuthorResult
import com.example.poetica.data.database.PoeticaDatabase
import com.example.poetica.data.mappers.ApiToDomainMapper
import com.example.poetica.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.Calendar
import kotlin.random.Random

class PoemRepository(
    private val poemDao: PoemDao,
    val context: Context,
    private val apiService: PoeticaApiService = ApiConfig.createApiService(context),
    private val config: PoeticaConfig? = null
) {
    
    companion object {
        private const val TAG = "PoemRepository"
        private const val MAX_CACHE_SIZE = 100
    }
    
    // Memory-optimized cache - metadata only for listings
    private val apiMetadataCache = mutableMapOf<String, PoemMetadata>()
    // Small full-content cache for recently accessed poems
    private val recentPoemCache = mutableMapOf<String, Poem>()
    private val maxRecentCacheSize = 10 // Keep only 10 recent full poems
    
    private fun shouldUseRemoteData(): Boolean {
        val effectiveConfig = config ?: PoeticaConfig.getInstance(context)
        val shouldUse = effectiveConfig.useRemoteData && effectiveConfig.isApiEnabled
        Log.d(TAG, "🔧 shouldUseRemoteData() -> useRemoteData=${effectiveConfig.useRemoteData}, isApiEnabled=${effectiveConfig.isApiEnabled}, result=$shouldUse")
        Log.d(TAG, "🔧 API URL: ${effectiveConfig.apiBaseUrl}")
        return shouldUse
    }
    
    suspend fun initializeWithBundledPoems() {
        withContext(Dispatchers.IO) {
            try {
                // First check if database is healthy
                val isHealthy = PoeticaDatabase.isDatabaseHealthy(context)
                if (!isHealthy) {
                    Log.w(TAG, "⚠️ Database health check failed, attempting recovery...")
                    PoeticaDatabase.recreateDatabase(context)
                    // Get fresh database instance after recreation
                    val newDb = PoeticaDatabase.getDatabase(context)
                    // Note: We can't reassign poemDao here as it's a constructor parameter,
                    // but the database recreation will handle the underlying connection
                }
                
                val count = poemDao.getPoemCount()
                Log.d(TAG, "📊 Database initialization: ${count} poems in database")
                
                if (count == 0) {
                    Log.w(TAG, "⚠️ Pre-populated database appears empty")
                    Log.w(TAG, "💡 This may indicate the pre-populated SQLite file is missing or corrupted")
                    Log.w(TAG, "💡 The app will continue with an empty database - poems can be added via API")
                } else {
                    Log.d(TAG, "✅ Using pre-populated database with ${count} poems")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Database initialization failed", e)
                // Don't throw - let the app continue with best effort
                Log.w(TAG, "⚠️ Continuing with potentially unstable database state")
            }
        }
    }
    
    fun getAllPoems(): Flow<List<Poem>> = poemDao.getAllPoems()
    
    // Memory-optimized methods
    fun getAllPoemsMetadata(): Flow<List<Poem>> = poemDao.getPoemsMetadataFlow()
    
    suspend fun getPoemsPage(limit: Int = 50, offset: Int = 0): List<Poem> {
        return poemDao.getPoemsMetadata(limit, offset)
    }
    
    suspend fun searchPoemsMetadata(query: String, limit: Int = 50, offset: Int = 0): List<Poem> {
        return poemDao.searchPoemsMetadata(query.trim(), limit, offset)
    }
    
    suspend fun getPoemById(id: String): Poem? {
        Log.d(TAG, "📖 getPoemById() called with id='$id'")
        
        return withContext(Dispatchers.IO) {
            // First check local database
            val localPoem = poemDao.getPoemById(id)
            if (localPoem != null) {
                // Log detailed content information
                val contentLength = localPoem.content.length
                val lineCount = localPoem.content.count { it == '\n' } + 1
                val paragraphCount = localPoem.content.split("\n\n").size
                val wordCount = localPoem.content.split("\\s+".toRegex()).size
                
                Log.d(TAG, "📖 ✅ Found poem in local database: '${localPoem.title}' by ${localPoem.author}")
                Log.d(TAG, "📊 DB content stats - Length: $contentLength chars, Lines: $lineCount, Paragraphs: $paragraphCount, Words: ~$wordCount")
                
                // Log first and last few characters to detect truncation
                val preview = localPoem.content.take(50).replace("\n", "\\n")
                val suffix = if (contentLength > 100) {
                    "..." + localPoem.content.takeLast(50).replace("\n", "\\n")
                } else ""
                Log.d(TAG, "📝 DB content: \"$preview$suffix\"")
                
                return@withContext localPoem
            }
            
            // Check recent full-content cache first
            val recentPoem = recentPoemCache[id]
            if (recentPoem != null) {
                Log.d(TAG, "📖 ✅ Found poem in recent cache: '${recentPoem.title}' by ${recentPoem.author}")
                return@withContext recentPoem
            }
            
            // Try to fetch from API using canonical_id
            if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Attempting API poem fetch for id='$id'")
                try {
                    val response = apiService.getPoem(id)
                    Log.d(TAG, "🌐 Poem API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val apiPoem = response.body()!!
                        val poem = ApiToDomainMapper.mapApiPoemToPoem(apiPoem)
                        // Cache both metadata and recent full content
                        cacheApiMetadata(listOf(poem))
                        cacheRecentPoem(poem)
                        Log.d(TAG, "🌐 ✅ Fetched poem from API: '${poem.title}' by ${poem.author}")
                        return@withContext poem
                    } else {
                        Log.w(TAG, "🌐 API poem fetch response not successful or empty")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "🌐 ❌ Failed to fetch poem from API: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "📖 ❌ Poem with id='$id' not found in database or cache")
            null
        }
    }
    
    suspend fun getPoemOfTheDay(): Poem? {
        Log.d(TAG, "🌅 getPoemOfTheDay() called")
        
        return withContext(Dispatchers.IO) {
            // Try local poems first for instant response - using optimized query
            Log.d(TAG, "🏠 Getting local poem of the day...")
            val poemCount = poemDao.getPoemCountForSelection()
            if (poemCount > 0) {
                // Use current date as seed for consistent daily selection
                val calendar = Calendar.getInstance()
                val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
                val year = calendar.get(Calendar.YEAR)
                val seed = (year * 1000L + dayOfYear).toLong()
                val random = Random(seed)
                val selectedIndex = random.nextInt(poemCount)
                
                // First get the ID, then load full content only when needed
                val selectedPoemId = poemDao.getPoemIdByIndex(selectedIndex)
                if (selectedPoemId != null) {
                    val selectedPoem = poemDao.getPoemById(selectedPoemId)
                    if (selectedPoem != null) {
                        Log.d(TAG, "🏠 ✅ Selected local POTD: '${selectedPoem.title}' by ${selectedPoem.author} (seed=$seed, index=$selectedIndex, total=$poemCount)")
                        return@withContext selectedPoem
                    }
                }
                Log.w(TAG, "🏠 ⚠️ Failed to load poem at index $selectedIndex, trying API fallback...")
            } else {
                Log.w(TAG, "🏠 ⚠️ No local poems available, trying API fallback...")
            }
            
            // Fallback to API only if local database is empty
            if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Attempting API poem of the day as fallback...")
                try {
                    val response = apiService.getRandomPoem(language = "en")
                    Log.d(TAG, "🌐 POTD API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val apiPoem = response.body()!!
                        val poem = ApiToDomainMapper.mapApiPoemToPoem(apiPoem)
                        // Cache both metadata and recent full content
                        cacheApiMetadata(listOf(poem))
                        cacheRecentPoem(poem)
                        Log.d(TAG, "🌐 ✅ Using API poem of the day as fallback: '${poem.title}' by ${poem.author}")
                        return@withContext poem
                    } else {
                        Log.w(TAG, "🌐 API POTD response not successful or empty")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "🌐 ❌ Failed to get poem of the day from API", e)
                }
            } else {
                Log.d(TAG, "🌐 API disabled, no fallback available")
            }
            
            Log.e(TAG, "❌ No poem of the day available from any source")
            null
        }
    }
    
    // Memory-optimized version - metadata only for listings
    fun getPoemsByAuthorMetadata(author: String): Flow<List<Poem>> = poemDao.getPoemsByAuthorMetadata(author)
    
    // Full content version - only when needed
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
                        poemLimit = ApiConfig.DEFAULT_SEARCH_LIMIT
                    )
                    
                    Log.d(TAG, "🌐 API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()!!
                        Log.d(TAG, "🌐 API response body received, parsing...")
                        val apiResults = ApiToDomainMapper.mapApiSearchResponseToSearchResults(responseBody)
                        Log.d(TAG, "🌐 API search results: ${apiResults.size} items found")
                        
                        if (apiResults.isNotEmpty()) {
                            // Cache metadata only for memory efficiency
                            cacheApiMetadata(apiResults.map { it.poem })
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
            
            // Fallback to local search with memory optimization
            Log.d(TAG, "🏠 Performing local search for: '$query'")
            val poems = poemDao.searchPoems(query.trim()) // Already limited to 100 results
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
    
    suspend fun getTopAuthors(limit: Int = 30): List<Author> {
        return withContext(Dispatchers.IO) {
            try {
                val authorResults = poemDao.getTopAuthorsWithCounts(limit)
                authorResults.map { result ->
                    Author(
                        name = result.name,
                        poemCount = result.poemCount
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading top authors", e)
                emptyList() // Return empty list instead of crashing
            }
        }
    }
    
    suspend fun getAuthorsPage(limit: Int = 50, offset: Int = 0): List<Author> {
        return withContext(Dispatchers.IO) {
            try {
                val authorResults = poemDao.getAuthorsWithCounts(limit, offset)
                authorResults.map { result ->
                    Author(
                        name = result.name,
                        poemCount = result.poemCount
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading authors page", e)
                emptyList()
            }
        }
    }
    
    suspend fun searchAuthors(query: String, limit: Int = 50): List<Author> {
        return withContext(Dispatchers.IO) {
            try {
                val authorResults = poemDao.searchAuthorsWithCounts(query.trim(), limit)
                authorResults.map { result ->
                    Author(
                        name = result.name,
                        poemCount = result.poemCount
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error searching authors", e)
                emptyList()
            }
        }
    }
    
    suspend fun getAuthorCount(): Int {
        return try {
            poemDao.getAuthorCount()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting author count", e)
            0
        }
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
    
    // Memory-efficient cache management methods
    private fun cacheApiMetadata(poems: List<Poem>) {
        poems.forEach { poem ->
            val metadata = PoemMetadata(
                id = poem.id,
                title = poem.title,
                author = poem.author,
                sourceType = poem.sourceType,
                firstLine = poem.content.lines().firstOrNull()?.take(100),
                wordCount = poem.content.split("\\s+".toRegex()).size
            )
            apiMetadataCache[poem.id] = metadata
        }
        
        Log.d(TAG, "💾 Cached ${poems.size} poem metadata. Metadata cache size: ${apiMetadataCache.size}")
        
        // Manage metadata cache size
        if (apiMetadataCache.size > MAX_CACHE_SIZE) {
            val keysToRemove = apiMetadataCache.keys.take(apiMetadataCache.size - MAX_CACHE_SIZE)
            keysToRemove.forEach { apiMetadataCache.remove(it) }
            Log.d(TAG, "💾 Cleaned metadata cache, removed ${keysToRemove.size} old entries")
        }
    }
    
    private fun cacheRecentPoem(poem: Poem) {
        recentPoemCache[poem.id] = poem
        
        // Manage recent cache size - keep only the most recently accessed
        if (recentPoemCache.size > maxRecentCacheSize) {
            val oldestKey = recentPoemCache.keys.first()
            recentPoemCache.remove(oldestKey)
            Log.d(TAG, "💾 Removed oldest poem from recent cache: $oldestKey")
        }
        
        Log.d(TAG, "💾 Cached recent poem: '${poem.title}'. Recent cache size: ${recentPoemCache.size}/$maxRecentCacheSize")
    }
    
    private fun getCacheStats(): String {
        return "Metadata: ${apiMetadataCache.size}/${MAX_CACHE_SIZE}, Recent: ${recentPoemCache.size}/$maxRecentCacheSize"
    }
    
    
}