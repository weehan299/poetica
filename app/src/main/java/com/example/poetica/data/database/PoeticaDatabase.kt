package com.example.poetica.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.poetica.data.converters.Converters
import com.example.poetica.data.model.Poem

@Database(
    entities = [Poem::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PoeticaDatabase : RoomDatabase() {
    
    abstract fun poemDao(): PoemDao
    
    companion object {
        @Volatile
        private var INSTANCE: PoeticaDatabase? = null
        
        fun getDatabase(context: Context): PoeticaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PoeticaDatabase::class.java,
                    "poetica_database"
                ).createFromAsset("databases/poetica_poems.db")
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}