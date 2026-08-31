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
    /**
     * Every mirrored note, newest first -- the whole table, not a page of it.
     *
     * Pagination stays on the remote side: `DefaultNoteRepository.getNotes(cursor)` walks Firestore
     * and writes each page in, and this re-emits as it does. That is the RemoteMediator shape
     * without the Paging 3 dependency, and it works here because the collection is one user's own
     * notes (`user/{uid}/note`) rather than a shared feed.
     */
    @Query("SELECT * FROM notes ORDER BY created_date DESC")
    fun getNotes(): Flow<List<DatabaseNote>>

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