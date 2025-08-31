package com.example.poetica.data.database

import androidx.room.*
import com.example.poetica.data.model.RecentSearch
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    
    @Query("SELECT * FROM recent_searches ORDER BY searchDate DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 15): Flow<List<RecentSearch>>
    
    @Query("SELECT * FROM recent_searches ORDER BY searchDate DESC")
    suspend fun getAllRecentSearches(): List<RecentSearch>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(recentSearch: RecentSearch)
    
    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteSearchByQuery(query: String)
    
    @Query("DELETE FROM recent_searches")
    suspend fun clearAllRecentSearches()
    
    @Query("DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY searchDate DESC LIMIT :limit)")
    suspend fun keepOnlyRecentSearches(limit: Int = 15)
    
    @Query("SELECT EXISTS(SELECT 1 FROM recent_searches WHERE query = :query)")
    suspend fun searchExists(query: String): Boolean
    
    @Query("SELECT COUNT(*) FROM recent_searches")
    suspend fun getRecentSearchCount(): Int
}