package com.jiahan.smartcamera.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.database.AppDatabase
import com.jiahan.smartcamera.database.data.DatabaseNote
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.MediaDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [NoteDao] running against a real (in-memory) Room database on device,
 * following the official Room testing guidance:
 * https://developer.android.com/training/data-storage/room/testing-db
 */
@RunWith(AndroidJUnit4::class)
class NoteDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var noteDao: NoteDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // In-memory DB is cleared from RAM after the process is killed — perfect test isolation.
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = database.noteDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun note(
        noteId: String,
        favorite: Boolean = true,
        createdDate: Long? = 0L,
        mediaList: List<MediaDetail>? = null,
    ) = DatabaseNote(
        noteId = noteId,
        text = "Note $noteId",
        createdDate = createdDate,
        favorite = favorite,
        mediaList = mediaList,
        username = "tester",
        profilePictureUrl = null,
    )

    /*
     * getNotes() is the mirror the feed will observe; getFavoriteNotes() is the favorites-only
     * query it sits beside. The pair below is the distinction that matters: the table stopped
     * being favorites-only, so one query has to see a non-favorited note and the other must not.
     */

    @Test
    fun getNotes_returnsEveryNoteRegardlessOfFavoriteFlag() = runBlocking {
        noteDao.upsertNotes(
            listOf(
                note("fav", favorite = true),
                note("notFav", favorite = false),
            )
        )

        val notes = noteDao.getNotes().first()

        assertEquals(2, notes.size)
        assertTrue(notes.any { it.noteId == "notFav" })
    }

    @Test
    fun getNotes_areOrderedByCreatedDateDescending() = runBlocking {
        noteDao.upsertNotes(
            listOf(
                note("old", favorite = false, createdDate = 100L),
                note("newest", favorite = false, createdDate = 300L),
                note("middle", favorite = true, createdDate = 200L),
            )
        )

        val notes = noteDao.getNotes().first()

        assertEquals(
            listOf("newest", "middle", "old"),
            notes.map { it.noteId }
        )
    }

    @Test
    fun getNotes_reEmitsWhenANoteIsUpserted() = runBlocking {
        noteDao.upsertNotes(listOf(note("first", favorite = false)))
        assertEquals(1, noteDao.getNotes().first().size)

        // The property the feed depends on: a page written in has to reach a live subscriber
        // without anyone re-querying.
        noteDao.upsertNotes(listOf(note("second", favorite = false)))

        assertEquals(2, noteDao.getNotes().first().size)
    }

    @Test
    fun upsertNotes_thenGetFavoriteNotes_returnsOnlyFavorites() = runBlocking {
        noteDao.upsertNotes(
            listOf(
                note("fav1", favorite = true),
                note("fav2", favorite = true),
                note("notFav", favorite = false),
            )
        )

        val favorites = noteDao.getFavoriteNotes().first()

        assertEquals(2, favorites.size)
        assertTrue(favorites.all { it.favorite })
        assertFalse(favorites.any { it.noteId == "notFav" })
    }

    @Test
    fun getFavoriteNotes_areOrderedByCreatedDateDescending() = runBlocking {
        noteDao.upsertNotes(
            listOf(
                note("old", createdDate = 100L),
                note("newest", createdDate = 300L),
                note("middle", createdDate = 200L),
            )
        )

        val favorites = noteDao.getFavoriteNotes().first()

        assertEquals(
            listOf("newest", "middle", "old"),
            favorites.map { it.noteId }
        )
    }

    @Test
    fun upsertNotes_replacesOnConflictByPrimaryKey() = runBlocking {
        noteDao.upsertNotes(listOf(note("doc", favorite = true).copy(text = "original")))
        noteDao.upsertNotes(listOf(note("doc", favorite = true).copy(text = "updated")))

        val favorites = noteDao.getFavoriteNotes().first()

        assertEquals(1, favorites.size)
        assertEquals("updated", favorites.first().text)
    }

    @Test
    fun deleteNote_removesMatchingNoteId() = runBlocking {
        noteDao.upsertNotes(listOf(note("keep"), note("remove")))

        noteDao.deleteNote("remove")

        val favorites = noteDao.getFavoriteNotes().first()
        assertEquals(1, favorites.size)
        assertEquals("keep", favorites.first().noteId)
    }

    @Test
    fun updateFavorite_toFalse_removesNoteFromFavorites() = runBlocking {
        noteDao.upsertNotes(listOf(note("doc", favorite = true)))

        noteDao.updateFavorite("doc", isFavorite = false)

        assertTrue(noteDao.getFavoriteNotes().first().isEmpty())
    }

    @Test
    fun clearFavorites_removesAllFavoriteNotes() = runBlocking {
        noteDao.upsertNotes(listOf(note("a"), note("b"), note("c")))

        noteDao.clearFavorites()

        assertTrue(noteDao.getFavoriteNotes().first().isEmpty())
    }

    @Test
    fun clearAllNotes_removesEveryNoteRegardlessOfFavoriteFlag() = runBlocking {
        noteDao.upsertNotes(listOf(note("fav", favorite = true), note("notFav", favorite = false)))

        noteDao.clearAllNotes()

        // getFavoriteNotes() alone can't distinguish this from clearFavorites(), since both leave
        // it empty — query the raw row count to confirm the non-favorite row is gone too.
        database.query("SELECT COUNT(*) FROM notes", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun syncFavoriteNotes_replacesExistingFavorites() = runBlocking {
        noteDao.upsertNotes(listOf(note("old1"), note("old2")))

        noteDao.syncFavoriteNotes(listOf(note("new1"), note("new2"), note("new3")))

        val favorites = noteDao.getFavoriteNotes().first()
        assertEquals(setOf("new1", "new2", "new3"), favorites.map { it.noteId }.toSet())
    }

    @Test
    fun mediaList_isPersistedAndRestoredViaTypeConverter() = runBlocking {
        val media = listOf(
            MediaDetail(
                photoUrl = "https://example.com/photo.jpg",
                generatedText = listOf("a cat"),
                generatedLabels = listOf(DetectedLabel("animal", 0.9)),
            )
        )
        noteDao.upsertNotes(listOf(note("doc", mediaList = media)))

        val restored = noteDao.getFavoriteNotes().first().first()

        assertEquals(1, restored.mediaList?.size)
        assertEquals("https://example.com/photo.jpg", restored.mediaList?.first()?.photoUrl)
        assertEquals(listOf("a cat"), restored.mediaList?.first()?.generatedText)
        assertEquals("animal", restored.mediaList?.first()?.generatedLabels?.first()?.label)
    }
}