package com.example.poetica.data.database

import androidx.room.*
import com.example.poetica.data.model.Poem
import kotlinx.coroutines.flow.Flow

@Dao
interface PoemDao {
    
    @Query("SELECT * FROM poems ORDER BY title ASC")
    fun getAllPoems(): Flow<List<Poem>>
    
    @Query("SELECT * FROM poems ORDER BY title ASC")
    suspend fun getAllPoemsSync(): List<Poem>
    
    @Query("SELECT * FROM poems WHERE id = :id")
    suspend fun getPoemById(id: String): Poem?
    
    @Query("SELECT * FROM poems WHERE author = :author ORDER BY title ASC")
    fun getPoemsByAuthor(author: String): Flow<List<Poem>>
    
    
    @Query("SELECT DISTINCT author FROM poems ORDER BY author ASC")
    suspend fun getAllAuthors(): List<String>
    
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoem(poem: Poem)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoems(poems: List<Poem>)
    
    @Update
    suspend fun updatePoem(poem: Poem)
    
    @Delete
    suspend fun deletePoem(poem: Poem)
    
    @Query("DELETE FROM poems")
    suspend fun deleteAllPoems()
    
    @Query("SELECT COUNT(*) FROM poems")
    suspend fun getPoemCount(): Int
}