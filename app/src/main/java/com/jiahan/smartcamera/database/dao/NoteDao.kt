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

    @Query("DELETE FROM notes WHERE note_id = :noteId")
    suspend fun deleteNote(noteId: String)

    @Query("UPDATE notes SET favorite = :isFavorite WHERE note_id = :noteId")
    suspend fun updateFavorite(noteId: String, isFavorite: Boolean)

    @Query("DELETE FROM notes WHERE favorite = 1")
    suspend fun clearFavorites()

    @Query("DELETE FROM notes")
    suspend fun clearAllNotes()

    @Transaction
    suspend fun syncFavoriteNotes(notes: List<DatabaseNote>) {
        clearFavorites()
        upsertNotes(notes)
    }
}