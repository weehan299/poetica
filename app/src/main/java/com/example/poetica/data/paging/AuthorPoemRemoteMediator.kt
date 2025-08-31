package com.example.poetica.data.paging

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.poetica.data.api.PoeticaApiService
import com.example.poetica.data.database.PoeticaDatabase
import com.example.poetica.data.mappers.ApiToDomainMapper
import com.example.poetica.data.model.AuthorPoemRemoteKeys
import com.example.poetica.data.model.Poem
import com.example.poetica.data.model.SourceType
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class AuthorPoemRemoteMediator(
    private val authorName: String,
    private val apiService: PoeticaApiService,
    private val database: PoeticaDatabase
) : RemoteMediator<Int, Poem>() {
    
    companion object {
        private const val TAG = "AuthorPoemRM"
        private const val STARTING_PAGE_INDEX = 1
        private const val DEFAULT_PAGE_SIZE = 20
    }

    override suspend fun initialize(): InitializeAction {
        Log.d(TAG, "🚀 initialize() called for author '$authorName'")
        // Only refresh if we have no cached data for this author
        val hasLocalData = hasDataForAuthor()
        return if (hasLocalData) {
            Log.d(TAG, "🚀 initialize() -> SKIP_INITIAL_REFRESH (has local data)")
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            Log.d(TAG, "🚀 initialize() -> LAUNCH_INITIAL_REFRESH (no local data)")
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Poem>
    ): MediatorResult {
        Log.d(TAG, "🔄 load() called - LoadType: $loadType, Author: '$authorName'")
        val page = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: STARTING_PAGE_INDEX
            }
            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                val prevKey = remoteKeys?.prevKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                prevKey
            }
            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                val nextKey = remoteKeys?.nextKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                nextKey
            }
        }

        try {
            Log.d(TAG, "🌐 Loading author poems - Author: '$authorName', Page: $page, LoadType: $loadType")
            
            val apiResponse = apiService.getPoems(
                page = page,
                size = DEFAULT_PAGE_SIZE,
                authorName = authorName,
                sort = "title",
                order = "asc"
            )

            if (!apiResponse.isSuccessful) {
                Log.w(TAG, "🌐 API response not successful: ${apiResponse.code()}")
                return MediatorResult.Error(HttpException(apiResponse))
            }

            val poemsResponse = apiResponse.body()
            if (poemsResponse == null) {
                Log.w(TAG, "🌐 API response body is null")
                return MediatorResult.Error(IOException("API response body is null"))
            }

            val apiPoems = poemsResponse.items
            Log.d(TAG, "🌐 Fetched ${apiPoems.size} poems from API for author '$authorName'")

            val endOfPaginationReached = !poemsResponse.hasNext
            Log.d(TAG, "🌐 End of pagination: $endOfPaginationReached")

            database.withTransaction {
                // Clear data only on refresh
                if (loadType == LoadType.REFRESH) {
                    Log.d(TAG, "💾 Clearing existing remote keys for refresh")
                    database.authorPoemRemoteKeysDao().clearRemoteKeys(authorName)
                    // Note: We don't clear local poems as they serve as the base data
                }

                val prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                
                val remoteKeys = AuthorPoemRemoteKeys(
                    author = authorName,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
                
                database.authorPoemRemoteKeysDao().insert(remoteKeys)
                Log.d(TAG, "💾 Saved remote keys: prevKey=$prevKey, nextKey=$nextKey")

                // Convert API poems to domain models and insert
                val poems = apiPoems.map { apiPoemListItem ->
                    // For list items, we only have first line, so we'll need to fetch full content later
                    // But we can still show the poem in the list with preview content
                    Poem(
                        id = apiPoemListItem.canonicalId,
                        title = apiPoemListItem.title,
                        author = apiPoemListItem.author.name,
                        content = apiPoemListItem.firstLine, // Preview content - full content fetched on-demand
                        sourceType = SourceType.REMOTE
                    )
                }

                // Insert/update poems (Room will handle duplicates with REPLACE strategy)
                database.poemDao().insertPoems(poems)
                Log.d(TAG, "💾 Inserted ${poems.size} poems into database")
            }

            Log.d(TAG, "✅ Successfully loaded page $page for author '$authorName'")
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)

        } catch (exception: IOException) {
            Log.e(TAG, "❌ Network error loading author poems", exception)
            return MediatorResult.Error(exception)
        } catch (exception: HttpException) {
            Log.e(TAG, "❌ HTTP error loading author poems", exception)
            return MediatorResult.Error(exception)
        } catch (exception: Exception) {
            Log.e(TAG, "❌ Unknown error loading author poems", exception)
            return MediatorResult.Error(exception)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, Poem>): AuthorPoemRemoteKeys? {
        return state.pages.lastOrNull()?.data?.lastOrNull()?.let { poem ->
            database.authorPoemRemoteKeysDao().remoteKeysForAuthor(authorName)
        }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, Poem>): AuthorPoemRemoteKeys? {
        return state.pages.firstOrNull()?.data?.firstOrNull()?.let { poem ->
            database.authorPoemRemoteKeysDao().remoteKeysForAuthor(authorName)
        }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, Poem>): AuthorPoemRemoteKeys? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.let { poem ->
                database.authorPoemRemoteKeysDao().remoteKeysForAuthor(authorName)
            }
        }
    }

    private suspend fun hasDataForAuthor(): Boolean {
        return try {
            Log.d(TAG, "🔍 Checking local data for author '$authorName'")
            // Use efficient EXISTS query instead of materializing all poems into memory
            val hasLocalData = database.poemDao().hasPoemsByAuthor(authorName)
            Log.d(TAG, "📊 Author '$authorName' has local data: $hasLocalData")
            hasLocalData
        } catch (e: Exception) {
            Log.w(TAG, "❌ Error checking local data for author '$authorName'", e)
            false
        }
    }
}