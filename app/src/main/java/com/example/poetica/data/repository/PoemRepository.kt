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
            Log.d(TAG, "📚 Loading bundled poems from assets...")
            val jsonString = context.assets.open("poems.json").bufferedReader().use { it.readText() }
            Log.d(TAG, "📁 JSON file size: ${jsonString.length} characters")
            
            val json = Json { ignoreUnknownKeys = true }
            val bundledPoems = json.decodeFromString<BundledPoems>(jsonString)
            val allPoems = bundledPoems.collections.flatMap { it.poems }
            
            Log.d(TAG, "📖 Parsed ${allPoems.size} poems from ${bundledPoems.collections.size} collections")
            
            // Log detailed stats for each poem being inserted
            allPoems.forEachIndexed { index, poem ->
                val contentLength = poem.content.length
                val lineCount = poem.content.count { it == '\n' } + 1
                val paragraphCount = poem.content.split("\n\n").size
                Log.d(TAG, "📊 Poem ${index + 1}/${allPoems.size}: '${poem.title}' by ${poem.author} - $contentLength chars, $lineCount lines, $paragraphCount paragraphs")
                
                // Log content preview for verification
                val preview = poem.content.take(80).replace("\n", "\\n")
                val suffix = if (contentLength > 160) {
                    "..." + poem.content.takeLast(80).replace("\n", "\\n")
                } else if (contentLength > 80) {
                    poem.content.drop(80).replace("\n", "\\n")
                } else ""
                Log.d(TAG, "📝 Content: \"$preview$suffix\"")
            }
            
            poemDao.insertPoems(allPoems)
            Log.d(TAG, "✅ Successfully inserted ${allPoems.size} bundled poems into database")
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to read poems.json from assets: ${e.message}", e)
            throw Exception("Failed to read poems.json from assets: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse poems JSON: ${e.message}", e)
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
                    val response = apiService.getPoems(
                        page = page,
                        size = pageSize,
                        language = "en"
                    )
                    Log.d(TAG, "🌐 Browse API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body()?.items?.isNotEmpty() == true) {
                        val apiPoems = response.body()!!.items.map { apiPoemListItem ->
                            ApiToDomainMapper.mapApiPoemListItemToPoem(apiPoemListItem)
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
            
            // If not found locally, check API cache first
            val cachedPoem = apiPoemCache[id]
            if (cachedPoem != null) {
                val contentLength = cachedPoem.content.length
                val lineCount = cachedPoem.content.count { it == '\n' } + 1
                val paragraphCount = cachedPoem.content.split("\n\n").size
                
                Log.d(TAG, "📖 ✅ Found poem in API cache: '${cachedPoem.title}' by ${cachedPoem.author}")
                Log.d(TAG, "📊 Cache content stats - Length: $contentLength chars, Lines: $lineCount, Paragraphs: $paragraphCount")
                
                // ALWAYS check if this cached poem has preview content and needs full content fetch
                Log.d(TAG, "🔍 Checking if cached poem has preview content...")
                val isPreview = isPoemContentPreview(cachedPoem)
                
                if (isPreview) {
                    Log.d(TAG, "🔄 Poem has preview content, attempting to fetch full content...")
                    val fullContentPoem = fetchFullContentForApiPoem(cachedPoem)
                    if (fullContentPoem != null) {
                        Log.d(TAG, "🔄 ✅ Successfully fetched full content")
                        return@withContext fullContentPoem
                    } else {
                        Log.w(TAG, "🔄 ⚠️ Failed to fetch full content, returning preview content")
                        return@withContext cachedPoem
                    }
                } else {
                    Log.d(TAG, "📖 ✅ Cached poem has full content, no fetch needed")
                }
                
                return@withContext cachedPoem
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
                        // Cache the fetched poem
                        cacheApiPoems(listOf(poem))
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
            // Try API first if enabled
            if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Attempting API poem of the day request...")
                try {
                    val response = apiService.getRandomPoem(language = "en")
                    Log.d(TAG, "🌐 POTD API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val apiPoem = response.body()!!
                        val poem = ApiToDomainMapper.mapApiPoemToPoem(apiPoem)
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
                        poemLimit = ApiConfig.DEFAULT_SEARCH_LIMIT
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
            val existingPoem = apiPoemCache[poem.id]
            
            // Only cache if:
            // 1. No existing poem in cache, OR
            // 2. Existing poem is preview content and new poem is full content, OR  
            // 3. New poem is not preview content (always cache full content)
            if (existingPoem == null || 
                (isPoemContentPreview(existingPoem) && !isPoemContentPreview(poem)) ||
                !isPoemContentPreview(poem)) {
                apiPoemCache[poem.id] = poem
                
                if (existingPoem != null && isPoemContentPreview(existingPoem) && !isPoemContentPreview(poem)) {
                    Log.d(TAG, "💾 ✅ Upgraded cached poem '${poem.title}' from preview to full content")
                }
            } else {
                Log.d(TAG, "💾 ⚠️ Skipping cache of preview content for '${poem.title}' (full content already cached)")
            }
        }
        
        Log.d(TAG, "💾 Processed ${poems.size} API poems for caching. Cache size: ${apiPoemCache.size}")
        
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
    
    
    /**
     * Determines if a poem likely contains only preview content based on content length and characteristics.
     * API previews are typically short and may contain truncated content indicators.
     */
    private fun isPoemContentPreview(poem: Poem): Boolean {
        val content = poem.content
        val contentLength = content.length
        
        Log.d(TAG, "🔍 isPoemContentPreview() called for '${poem.title}' by ${poem.author}")
        Log.d(TAG, "🔍 Content length: $contentLength, First 50 chars: \"${content.take(50).replace("\n", "\\n")}...\"")
        
        // Only check API poems
        if (poem.sourceType != SourceType.REMOTE) {
            Log.d(TAG, "🔍 Not remote source, returning false")
            return false
        }
        
        // Heuristics for detecting preview content:
        // 1. Short content (<= 250 chars) - API returns ~200-201 char previews
        // 2. Content starts with "..." indicating mid-text excerpt  
        // 3. Content contains "..." indicating truncation
        // 4. Content seems to end abruptly (no proper ending punctuation)
        // 5. Content appears to be just first line or preview
        
        val isShort = contentLength <= 250
        val startsWithEllipsis = content.trimStart().startsWith("...")
        val containsEllipsis = content.contains("...")
        val endsAbruptly = !content.takeLast(50).contains(Regex("[.!?]\\s*$"))
        val looksLikeFirstLine = content.lines().size <= 2 && contentLength < 150
        
        val isPreview = isShort || startsWithEllipsis || containsEllipsis || endsAbruptly || looksLikeFirstLine
        
        Log.d(TAG, "🔍 Preview detection criteria:")
        Log.d(TAG, "🔍   - isShort (≤250): $isShort")
        Log.d(TAG, "🔍   - startsWithEllipsis: $startsWithEllipsis") 
        Log.d(TAG, "🔍   - containsEllipsis: $containsEllipsis")
        Log.d(TAG, "🔍   - endsAbruptly: $endsAbruptly")
        Log.d(TAG, "🔍   - looksLikeFirstLine: $looksLikeFirstLine")
        Log.d(TAG, "🔍   - RESULT: $isPreview")
        
        if (isPreview) {
            Log.d(TAG, "🔍 ✅ DETECTED PREVIEW CONTENT for '${poem.title}' - will fetch full content")
        } else {
            Log.d(TAG, "🔍 ✅ FULL CONTENT detected for '${poem.title}' - no fetch needed")
        }
        
        return isPreview
    }
    
    /**
     * Fetches full content for an API poem that currently has only preview content.
     * Uses the canonical_id to call the new /api/poems/{canonical_id} endpoint.
     */
    private suspend fun fetchFullContentForApiPoem(poem: Poem): Poem? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Fetching full content for poem ID: ${poem.id} (poem: '${poem.title}')")
                
                val response = apiService.getPoem(poem.id)
                Log.d(TAG, "🔄 Full content API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                
                if (response.isSuccessful && response.body() != null) {
                    val apiPoem = response.body()!!
                    val fullPoem = ApiToDomainMapper.mapApiPoemToPoem(apiPoem)
                    
                    // Log the content upgrade
                    val oldLength = poem.content.length
                    val newLength = fullPoem.content.length
                    Log.d(TAG, "🔄 ✅ Content upgraded: $oldLength → $newLength chars (+${newLength - oldLength})")
                    Log.d(TAG, "🔄 Full content preview: \"${fullPoem.content.take(100).replace("\n", "\\n")}...\"")
                    
                    // Update cache with full content
                    apiPoemCache[fullPoem.id] = fullPoem
                    
                    return@withContext fullPoem
                } else {
                    Log.w(TAG, "🔄 ❌ Failed to fetch full content for poem ${poem.id}: ${response.code()} - ${response.message()}")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🔄 ❌ Exception fetching full content for poem '${poem.title}': ${e.message}", e)
                return@withContext null
            }
        }
    }
    
}