package com.example.poetica.data.repository

import android.content.Context
import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.poetica.data.api.ApiConfig
import com.example.poetica.data.api.PoeticaApiService
import com.example.poetica.data.config.PoeticaConfig
import com.example.poetica.data.database.PoemDao
import com.example.poetica.data.database.AuthorResult
import com.example.poetica.data.database.PoeticaDatabase
import com.example.poetica.data.mappers.ApiToDomainMapper
import com.example.poetica.data.model.*
import com.example.poetica.data.paging.AuthorPoemRemoteMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val database: PoeticaDatabase,
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
    
    @Deprecated("Memory-intensive: loads all poems with full content. Use getAllPoemsMetadata() or paging instead.", 
        ReplaceWith("getAllPoemsMetadata()"))
    fun getAllPoems(): Flow<List<Poem>> {
        Log.w(TAG, "⚠️ getAllPoems() called - this loads ${33651}+ poems with full content into memory!")
        Log.w(TAG, "⚠️ Consider using getAllPoemsMetadata() or paging for better memory efficiency")
        return poemDao.getAllPoems()
    }
    
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
                
                // Check if this is preview content that needs upgrading
                if (isPreviewContent(localPoem) && shouldUseRemoteData()) {
                    Log.d(TAG, "🔄 Detected preview content, attempting to upgrade from API...")
                    try {
                        val response = apiService.getPoem(id)
                        Log.d(TAG, "🌐 Content upgrade API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                        
                        if (response.isSuccessful && response.body() != null) {
                            val apiPoem = response.body()!!
                            val upgradedPoem = ApiToDomainMapper.mapApiPoemToPoem(apiPoem)
                            
                            // Update the local database with full content
                            poemDao.updatePoem(upgradedPoem)
                            
                            // Cache the upgraded content
                            cacheRecentPoem(upgradedPoem)
                            
                            Log.d(TAG, "🔄 ✅ Successfully upgraded poem content from ${localPoem.content.length} to ${upgradedPoem.content.length} chars")
                            Log.d(TAG, "📝 Upgraded content preview: \"${upgradedPoem.content.take(100).replace("\n", "\\n")}...\"")
                            
                            return@withContext upgradedPoem
                        } else {
                            Log.w(TAG, "🔄 ⚠️ Failed to upgrade content from API, using local preview")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "🔄 ❌ Error upgrading content from API: ${e.message}", e)
                    }
                }
                
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
    
    suspend fun getRandomLocalPoem(): Poem? {
        Log.d(TAG, "🎲 getRandomLocalPoem() called")
        
        return withContext(Dispatchers.IO) {
            try {
                val randomPoem = poemDao.getRandomPoem()
                if (randomPoem != null) {
                    Log.d(TAG, "🎲 ✅ Selected random local poem: '${randomPoem.title}' by ${randomPoem.author}")
                    return@withContext randomPoem
                } else {
                    Log.w(TAG, "🎲 ❌ No random poem found in local database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "🎲 ❌ Failed to get random local poem", e)
            }
            null
        }
    }
    
    // Memory-optimized version - metadata only for listings
    fun getPoemsByAuthorMetadata(author: String): Flow<List<Poem>> = poemDao.getPoemsByAuthorMetadata(author)
    
    // Full content version - only when needed
    @Deprecated("Memory-intensive: loads all poems with full content for an author. Use getPoemsByAuthorMetadata() or paging instead.",
        ReplaceWith("getPoemsByAuthorMetadata(author)"))
    fun getPoemsByAuthor(author: String): Flow<List<Poem>> {
        Log.w(TAG, "⚠️ getPoemsByAuthor() called for '$author' - this loads all poems with full content into memory!")
        Log.w(TAG, "⚠️ Consider using getPoemsByAuthorMetadata() or getAuthorPoemsPagedFlow() for better memory efficiency")
        return poemDao.getPoemsByAuthor(author)
    }
    
    // Paging 3 support with RemoteMediator for hybrid local + remote data
    @OptIn(ExperimentalPagingApi::class)
    fun getAuthorPoemsPagedFlow(author: String): Flow<PagingData<Poem>> {
        Log.d(TAG, "🔄 Creating paged flow for author: '$author'")
        
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            ),
            remoteMediator = if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Using RemoteMediator for author '$author'")
                AuthorPoemRemoteMediator(
                    authorName = author,
                    apiService = apiService,
                    database = database
                )
            } else {
                Log.d(TAG, "🏠 RemoteMediator disabled, local-only paging for author '$author'")
                null
            },
            pagingSourceFactory = {
                Log.d(TAG, "📄 🧠 Creating MEMORY-OPTIMIZED PagingSource for author '$author'")
                Log.d(TAG, "📄 💾 Using metadata-only queries (no content field) to prevent OOM crashes")
                poemDao.getPoemsByAuthorPagedMetadata(author)
            }
        ).flow
    }
    
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
    
    suspend fun searchMixedResults(query: String): Flow<List<SearchResultItem>> = flow {
        Log.d(TAG, "🔍 searchMixedResults() called with query: '$query'")
        
        if (query.isBlank()) {
            Log.d(TAG, "🔍 Empty query, returning empty results")
            emit(emptyList())
            return@flow
        }
        
        val results = withContext(Dispatchers.IO) {
            // Try API search first if enabled
            if (shouldUseRemoteData()) {
                Log.d(TAG, "🌐 Attempting API search for mixed results: '$query' (60s timeout)")
                try {
                    // Add 60-second timeout to remote search
                    val apiResults = withTimeout(60_000L) {
                        Log.d(TAG, "🌐 Making API call to search endpoint...")
                        val response = apiService.search(
                            query = query.trim(),
                            poemLimit = ApiConfig.DEFAULT_SEARCH_LIMIT
                        )
                        
                        Log.d(TAG, "🌐 API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
                        
                        if (response.isSuccessful && response.body() != null) {
                            val responseBody = response.body()!!
                            Log.d(TAG, "🌐 API response body received, parsing mixed results...")
                            val results = ApiToDomainMapper.mapApiSearchResponseToSearchResultItems(responseBody)
                            Log.d(TAG, "🌐 API mixed search results: ${results.size} items found")
                            results
                        } else {
                            Log.w(TAG, "🌐 API response not successful or empty body: ${response.code()} - ${response.message()}")
                            response.errorBody()?.let {
                                Log.w(TAG, "🌐 Error body: ${it.string()}")
                            }
                            emptyList<SearchResultItem>()
                        }
                    }
                    
                    if (apiResults.isNotEmpty()) {
                        // Cache poem metadata only for memory efficiency
                        val poems = apiResults.filterIsInstance<SearchResultItem.PoemResult>().map { it.searchResult.poem }
                        cacheApiMetadata(poems)
                        Log.d(TAG, "🌐 ✅ Using API mixed results (${apiResults.size} items)")
                        return@withContext apiResults
                    } else {
                        Log.d(TAG, "🌐 API returned empty results, falling back to local")
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "🌐 ⏱️ API search timed out after 60 seconds for '$query', falling back to local")
                } catch (e: Exception) {
                    Log.w(TAG, "🌐 ❌ API search failed for '$query', falling back to local", e)
                }
            } else {
                Log.d(TAG, "🏠 Skipping API search, using local search only")
            }
            
            // Fallback to local search with memory optimization
            Log.d(TAG, "🏠 📱 Remote search failed/empty, performing local fallback search for: '$query'")
            val poems = poemDao.searchPoems(query.trim()) // Already limited to 100 results
            val authors = poemDao.searchAuthorsWithCounts(query.trim(), 10)
            Log.d(TAG, "🏠 Local database search found: ${poems.size} poems, ${authors.size} authors")
            
            val localResults = mutableListOf<SearchResultItem>()
            
            // Add local authors if query matches author names
            authors.forEach { authorResult ->
                val authorSearchResult = AuthorSearchResult(
                    author = Author(
                        name = authorResult.name,
                        poemCount = authorResult.poemCount
                    ),
                    matchType = determineMatchType(authorResult.name, query),
                    relevanceScore = calculateAuthorLocalRelevanceScore(authorResult.name, query)
                )
                localResults.add(SearchResultItem.AuthorResult(authorSearchResult))
            }
            
            // Add local poems
            poems.forEach { poem ->
                val searchResult = SearchResult(
                    poem = poem,
                    matchType = determineMatchType(poem, query),
                    relevanceScore = calculateRelevanceScore(poem, query)
                )
                localResults.add(SearchResultItem.PoemResult(searchResult))
            }
            
            val sortedResults = localResults.sortedByDescending { item ->
                when (item) {
                    is SearchResultItem.AuthorResult -> item.authorSearchResult.relevanceScore
                    is SearchResultItem.PoemResult -> item.searchResult.relevanceScore
                }
            }
            
            val authorCount = sortedResults.count { it is SearchResultItem.AuthorResult }
            val poemCount = sortedResults.count { it is SearchResultItem.PoemResult }
            Log.d(TAG, "🏠 ✅ Returning local mixed results: ${sortedResults.size} total ($authorCount authors, $poemCount poems)")
            sortedResults
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
    
    private fun determineMatchType(authorName: String, query: String): MatchType {
        val queryLower = query.lowercase()
        val authorLower = authorName.lowercase()
        
        return when {
            authorLower == queryLower -> MatchType.AUTHOR_EXACT
            authorLower.contains(queryLower) -> MatchType.AUTHOR_PARTIAL
            else -> MatchType.AUTHOR_PARTIAL // Default for fuzzy matches
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
    
    private fun calculateAuthorLocalRelevanceScore(authorName: String, query: String): Float {
        val queryLower = query.lowercase()
        val authorLower = authorName.lowercase()
        
        var score = 0f
        
        // Author name matches get high priority
        when {
            authorLower == queryLower -> score += 150f // Higher than poems for exact author matches
            authorLower.startsWith(queryLower) -> score += 120f
            authorLower.contains(queryLower) -> score += 100f
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
    
    private fun isPreviewContent(poem: Poem): Boolean {
        // Detect if this poem contains only preview content (first line) and needs full content upgrade
        val contentLength = poem.content.length
        val lineCount = poem.content.count { it == '\n' } + 1
        val wordCount = poem.content.split("\\s+".toRegex()).size
        
        // Consider it preview content if:
        // 1. It's from a remote source AND
        // 2. It's very short (< 100 chars) AND only 1 line AND has few words (< 15)
        // 3. OR it appears to end abruptly (no punctuation at end, suggesting truncation)
        val isShortSingleLine = contentLength < 100 && lineCount == 1 && wordCount < 15
        val trimmedContent = poem.content.trim()
        val appearsIncomplete = trimmedContent.isNotEmpty() && 
                               !trimmedContent.endsWith('.') && 
                               !trimmedContent.endsWith('!') && 
                               !trimmedContent.endsWith('?') && 
                               !trimmedContent.endsWith(':') && 
                               !trimmedContent.endsWith(';') && 
                               !trimmedContent.endsWith('"') && 
                               !trimmedContent.endsWith(')') && 
                               !trimmedContent.endsWith(']') && 
                               !trimmedContent.endsWith('}')
        
        val isLikelyPreview = poem.sourceType == SourceType.REMOTE && 
                             (isShortSingleLine || (contentLength < 200 && appearsIncomplete))
        
        if (isLikelyPreview) {
            Log.d(TAG, "🔍 Detected preview content for '${poem.title}' - Length: $contentLength, Lines: $lineCount, Words: $wordCount")
        }
        
        return isLikelyPreview
    }
    
}