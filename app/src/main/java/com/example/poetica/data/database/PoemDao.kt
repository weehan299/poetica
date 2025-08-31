package com.example.poetica.data.database

import androidx.paging.PagingSource
import androidx.room.*
import com.example.poetica.data.model.Poem
import kotlinx.coroutines.flow.Flow

data class AuthorResult(
    val name: String,
    val poemCount: Int
)

@Dao
interface PoemDao {
    
    @Query("SELECT * FROM poems ORDER BY title ASC")
    fun getAllPoems(): Flow<List<Poem>>
    
    @Query("SELECT * FROM poems ORDER BY title ASC")
    suspend fun getAllPoemsSync(): List<Poem>
    
    // Memory-optimized methods
    
    // Memory-optimized version - gets ID for selection, then load full content
    @Query("SELECT id FROM poems ORDER BY title ASC LIMIT 1 OFFSET :index")
    suspend fun getPoemIdByIndex(index: Int): String?
    
    // Full content version - load full poem by index
    @Query("SELECT * FROM poems LIMIT 1 OFFSET :index")
    suspend fun getPoemByIndex(index: Int): Poem?
    
    @Query("SELECT COUNT(*) FROM poems")
    suspend fun getPoemCountForSelection(): Int
    
    @Query("SELECT id, title, author, '' as content, sourceType FROM poems ORDER BY title ASC LIMIT :limit OFFSET :offset")
    suspend fun getPoemsMetadata(limit: Int, offset: Int): List<Poem>
    
    @Query("SELECT id, title, author, '' as content, sourceType FROM poems ORDER BY title ASC")
    fun getPoemsMetadataFlow(): Flow<List<Poem>>
    
    @Query("SELECT * FROM poems WHERE id = :id")
    suspend fun getPoemById(id: String): Poem?
    
    // Memory-optimized version - metadata only for listings
    @Query("SELECT id, title, author, '' as content, sourceType FROM poems WHERE author = :author ORDER BY title ASC")
    fun getPoemsByAuthorMetadata(author: String): Flow<List<Poem>>
    
    // Full content version - only use when specifically needed
    @Query("SELECT * FROM poems WHERE author = :author ORDER BY title ASC")
    fun getPoemsByAuthor(author: String): Flow<List<Poem>>
    
    // Paging 3 support for author poems - MEMORY OPTIMIZED (metadata only)
    @Query("SELECT id, title, author, '' as content, sourceType FROM poems WHERE author = :author ORDER BY title ASC")
    fun getPoemsByAuthorPagedMetadata(author: String): PagingSource<Int, Poem>
    
    // Legacy paging with full content - AVOID using this for large lists (can cause OOM)
    @Query("SELECT * FROM poems WHERE author = :author ORDER BY title ASC")
    fun getPoemsByAuthorPaged(author: String): PagingSource<Int, Poem>
    
    
    @Query("""
        SELECT * FROM poems 
        WHERE title LIKE '%' || :query || '%' 
        OR author LIKE '%' || :query || '%' 
        OR content LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN title LIKE :query || '%' THEN 1
                WHEN author LIKE :query || '%' THEN 2
                WHEN title LIKE '%' || :query || '%' THEN 3
                WHEN author LIKE '%' || :query || '%' THEN 4
                ELSE 5
            END,
            title ASC
        LIMIT 100
    """)
    suspend fun searchPoems(query: String): List<Poem>
    
    @Query("""
        SELECT id, title, author, '' as content, sourceType FROM poems 
        WHERE title LIKE '%' || :query || '%' 
        OR author LIKE '%' || :query || '%' 
        ORDER BY 
            CASE 
                WHEN title LIKE :query || '%' THEN 1
                WHEN author LIKE :query || '%' THEN 2
                WHEN title LIKE '%' || :query || '%' THEN 3
                WHEN author LIKE '%' || :query || '%' THEN 4
                ELSE 5
            END,
            title ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchPoemsMetadata(query: String, limit: Int, offset: Int): List<Poem>
    
    @Query("SELECT DISTINCT author FROM poems ORDER BY author ASC")
    suspend fun getAllAuthors(): List<String>
    
    @Query("""
        SELECT author as name, COUNT(*) as poemCount 
        FROM poems 
        GROUP BY author 
        ORDER BY poemCount DESC, author ASC
        LIMIT :limit
    """)
    suspend fun getTopAuthorsWithCounts(limit: Int = 30): List<AuthorResult>
    
    @Query("""
        SELECT author as name, COUNT(*) as poemCount 
        FROM poems 
        GROUP BY author 
        ORDER BY author ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAuthorsWithCounts(limit: Int, offset: Int): List<AuthorResult>
    
    @Query("""
        SELECT author as name, COUNT(*) as poemCount 
        FROM poems 
        WHERE author LIKE '%' || :query || '%'
        GROUP BY author 
        ORDER BY 
            CASE WHEN author LIKE :query || '%' THEN 1 ELSE 2 END,
            author ASC
        LIMIT :limit
    """)
    suspend fun searchAuthorsWithCounts(query: String, limit: Int = 50): List<AuthorResult>
    
    @Query("SELECT COUNT(DISTINCT author) FROM poems")
    suspend fun getAuthorCount(): Int
    
    
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
    
    @Query("SELECT EXISTS(SELECT 1 FROM poems WHERE author = :author)")
    suspend fun hasPoemsByAuthor(author: String): Boolean
    
    @Query("SELECT * FROM poems ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomPoem(): Poem?
}