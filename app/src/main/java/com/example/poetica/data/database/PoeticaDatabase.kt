package com.example.poetica.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import android.util.Log
import com.example.poetica.data.converters.Converters
import com.example.poetica.data.model.Poem
import java.io.File

@Database(
    entities = [Poem::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PoeticaDatabase : RoomDatabase() {
    
    abstract fun poemDao(): PoemDao
    
    companion object {
        @Volatile
        private var INSTANCE: PoeticaDatabase? = null
        private const val TAG = "PoeticaDatabase"
        private const val DATABASE_NAME = "poetica_database"
        private const val ASSET_PATH = "databases/poetica_poems.db"
        
        fun getDatabase(context: Context): PoeticaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }
                
                Log.d(TAG, "🗄️ Initializing database...")
                
                val instance = try {
                    createDatabaseInstance(context)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Database creation failed, attempting recovery", e)
                    attemptDatabaseRecovery(context)
                }
                
                INSTANCE = instance
                Log.d(TAG, "✅ Database initialized successfully")
                instance
            }
        }
        
        private fun createDatabaseInstance(context: Context): PoeticaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PoeticaDatabase::class.java,
                DATABASE_NAME
            ).createFromAsset(ASSET_PATH)
            .fallbackToDestructiveMigration()
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // More stable than WAL
            .build()
        }
        
        private fun attemptDatabaseRecovery(context: Context): PoeticaDatabase {
            Log.w(TAG, "🔄 Attempting database recovery...")
            
            // Clear any corrupted database files
            clearDatabaseFiles(context)
            
            // Try creating database again
            return try {
                createDatabaseInstance(context)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Database recovery failed", e)
                // Create a minimal database without pre-populated data as last resort
                createFallbackDatabase(context)
            }
        }
        
        private fun clearDatabaseFiles(context: Context) {
            try {
                val dbPath = context.getDatabasePath(DATABASE_NAME)
                val dbDir = dbPath.parentFile
                
                if (dbDir?.exists() == true) {
                    dbDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith(DATABASE_NAME)) {
                            val deleted = file.delete()
                            Log.d(TAG, "🗑️ Deleted database file: ${file.name} (success: $deleted)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error clearing database files", e)
            }
        }
        
        private fun createFallbackDatabase(context: Context): PoeticaDatabase {
            Log.w(TAG, "⚠️ Creating fallback database without pre-populated data")
            return Room.databaseBuilder(
                context.applicationContext,
                PoeticaDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration()
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        }
        
        // Method to check if database is healthy
        suspend fun isDatabaseHealthy(context: Context): Boolean {
            return try {
                val instance = getDatabase(context)
                instance.poemDao().getPoemCount() >= 0
                true
            } catch (e: Exception) {
                Log.w(TAG, "❌ Database health check failed", e)
                false
            }
        }
        
        // Method to force database recreation (for testing/recovery)
        fun recreateDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                clearDatabaseFiles(context)
                Log.i(TAG, "🔄 Database recreation initiated")
            }
        }
    }
}