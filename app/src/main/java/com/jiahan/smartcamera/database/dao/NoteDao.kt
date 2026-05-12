package com.jiahan.smartcamera.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jiahan.smartcamera.database.data.DatabaseNote
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE favorite = 1 ORDER BY created_date DESC")
    fun getFavoriteNotes(): Flow<List<DatabaseNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<DatabaseNote>)

    @Query("DELETE FROM notes WHERE document_path = :documentPath")
    suspend fun deleteNote(documentPath: String)

    @Query("UPDATE notes SET favorite = :isFavorite WHERE document_path = :documentPath")
    suspend fun updateFavorite(documentPath: String, isFavorite: Boolean)

    @Query("DELETE FROM notes WHERE favorite = 1")
    suspend fun clearFavorites()

    @Transaction
    suspend fun syncFavoriteNotes(notes: List<DatabaseNote>) {
        clearFavorites()
        upsertNotes(notes)
    }
}