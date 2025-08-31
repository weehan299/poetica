package com.example.poetica.data.database

import androidx.room.*
import com.example.poetica.data.model.AuthorPoemRemoteKeys

@Dao
interface AuthorPoemRemoteKeysDao {
    
    @Query("SELECT * FROM author_poem_remote_keys WHERE author = :author")
    suspend fun remoteKeysForAuthor(author: String): AuthorPoemRemoteKeys?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKey: List<AuthorPoemRemoteKeys>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(remoteKey: AuthorPoemRemoteKeys)
    
    @Query("DELETE FROM author_poem_remote_keys WHERE author = :author")
    suspend fun clearRemoteKeys(author: String)
    
    @Query("DELETE FROM author_poem_remote_keys")
    suspend fun clearAllRemoteKeys()
}