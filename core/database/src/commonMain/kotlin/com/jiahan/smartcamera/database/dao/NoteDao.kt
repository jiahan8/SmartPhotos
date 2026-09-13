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

    /**
     * The newest [limit] mirrored notes -- the feed's window, not the whole table.
     *
     * Home paginates remotely and widens this as it goes, so what it renders matches what it has
     * actually paged rather than everything the table happens to hold. Without the limit any other
     * write into `notes` (a search topping up the mirror, say) would silently appear in the feed.
     */
    @Query("SELECT * FROM notes ORDER BY created_date DESC LIMIT :limit")
    fun getNotes(limit: Int): Flow<List<DatabaseNote>>

    /** One mirrored note, or null once it is deleted. */
    @Query("SELECT * FROM notes WHERE note_id = :noteId")
    fun getNote(noteId: String): Flow<DatabaseNote?>

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