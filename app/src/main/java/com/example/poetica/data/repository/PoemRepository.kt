package com.example.poetica.data.repository

import android.content.Context
import com.example.poetica.data.database.PoemDao
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
    private val context: Context
) {
    
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
    
    suspend fun getPoemById(id: String): Poem? = poemDao.getPoemById(id)
    
    suspend fun getPoemOfTheDay(): Poem? {
        return withContext(Dispatchers.IO) {
            val allPoems = poemDao.getAllPoemsSync()
            if (allPoems.isEmpty()) return@withContext null
            
            // Use current date as seed for consistent daily selection
            val calendar = Calendar.getInstance()
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val year = calendar.get(Calendar.YEAR)
            val seed = (year * 1000L + dayOfYear).toLong()
            val random = Random(seed)
            val selectedIndex = random.nextInt(allPoems.size)
            
            allPoems[selectedIndex]
        }
    }
    
    fun getPoemsByAuthor(author: String): Flow<List<Poem>> = poemDao.getPoemsByAuthor(author)
    
    
    suspend fun getAllAuthors(): List<String> = poemDao.getAllAuthors()
    
    suspend fun getAllTags(): List<String> {
        val rawTags = poemDao.getAllTagsRaw()
        return rawTags.flatMap { tagListString ->
            try {
                Json.decodeFromString<List<String>>(tagListString)
            } catch (e: Exception) {
                emptyList()
            }
        }.distinct().sorted()
    }
    
    suspend fun insertPoem(poem: Poem) = poemDao.insertPoem(poem)
    
    suspend fun updatePoem(poem: Poem) = poemDao.updatePoem(poem)
    
    suspend fun deletePoem(poem: Poem) = poemDao.deletePoem(poem)
    
    
}