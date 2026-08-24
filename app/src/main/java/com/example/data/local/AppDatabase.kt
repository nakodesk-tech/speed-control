package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyNoteDao {
    @Query("SELECT * FROM study_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes WHERE packageName = :pkg ORDER BY timestamp DESC")
    fun getNotesForPackage(pkg: String): Flow<List<StudyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: StudyNoteEntity): Long

    @Delete
    suspend fun deleteNote(note: StudyNoteEntity)

    @Query("DELETE FROM study_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)
}

@Dao
interface TraversalLogDao {
    @Query("SELECT * FROM traversal_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<TraversalLogEntity>>

    @Insert
    suspend fun insertLog(log: TraversalLogEntity): Long

    @Query("DELETE FROM traversal_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [StudyNoteEntity::class, StudySessionEntity::class, TraversalLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyNoteDao(): StudyNoteDao
    abstract fun traversalLogDao(): TraversalLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "educompanion_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
