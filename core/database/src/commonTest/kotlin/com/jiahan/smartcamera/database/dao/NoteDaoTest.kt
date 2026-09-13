package com.jiahan.smartcamera.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import com.jiahan.smartcamera.database.AppDatabase
import com.jiahan.smartcamera.database.data.DatabaseNote
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.MediaDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [NoteDao] against a real in-memory Room database, following the official Room testing
 * guidance: https://developer.android.com/training/data-storage/room/testing-db
 *
 * In `commonTest`, so the suite runs on the JVM in CI and on an iOS simulator on a Mac, against
 * Room's bundled SQLite. It used to be a `sharedTest` suite in :core:data, running under
 * Robolectric and on a device against Android's framework SQLite; it followed [AppDatabase] down
 * when the database moved to this module. The queries are plain SQL that both engines answer
 * alike. What stays pinned to Android's own SQLite is the upgrade path, which is why
 * `AppDatabaseMigrationTest` did not come along.
 *
 * `Room.inMemoryDatabaseBuilder` without a `Context` exists on the JVM and Apple targets but not on
 * Android, which is why the Android target declares no host tests: this source set would not
 * compile for one.
 */
class NoteDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var noteDao: NoteDao

    @BeforeTest
    fun createDb() {
        // In-memory: each test gets a fresh database that is gone once closed.
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        noteDao = database.noteDao()
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    private fun note(
        noteId: String,
        isFavorite: Boolean = true,
        createdDate: Long? = 0L,
        mediaList: List<MediaDetail>? = null,
    ) = DatabaseNote(
        noteId = noteId,
        text = "Note $noteId",
        createdDate = createdDate,
        isFavorite = isFavorite,
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
    fun getNotes_returnsEveryNoteRegardlessOfFavoriteFlag() = runTest {
        noteDao.upsertNotes(
            listOf(
                note("fav", isFavorite = true),
                note("notFav", isFavorite = false),
            )
        )

        val notes = noteDao.getNotes().first()

        assertEquals(2, notes.size)
        assertTrue(notes.any { it.noteId == "notFav" })
    }

    @Test
    fun getNotes_areOrderedByCreatedDateDescending() = runTest {
        noteDao.upsertNotes(
            listOf(
                note("old", isFavorite = false, createdDate = 100L),
                note("newest", isFavorite = false, createdDate = 300L),
                note("middle", isFavorite = true, createdDate = 200L),
            )
        )

        val notes = noteDao.getNotes().first()

        assertEquals(
            listOf("newest", "middle", "old"),
            notes.map { it.noteId }
        )
    }

    /**
     * The property the feed depends on, asserted against *one* subscriber.
     *
     * This used to call `getNotes().first()` twice with the write in between, which is two separate
     * collections -- it re-queried, which is precisely what the feed never does, and it would have
     * passed against a `flow { emit(query()) }` that is not reactive at all. Turbine keeps a single
     * collection open across the write, so the second item can only arrive by re-emission.
     */
    @Test
    fun getNotes_reEmitsToAnAlreadyCollectingSubscriber() = runTest {
        noteDao.upsertNotes(listOf(note("first", isFavorite = false)))

        noteDao.getNotes().test {
            assertEquals(listOf("first"), awaitItem().map { it.noteId })

            noteDao.upsertNotes(listOf(note("second", isFavorite = false)))

            assertEquals(setOf("first", "second"), awaitItem().map { it.noteId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The same guarantee for the favorites query, which the Favorite screen reads the same way. */
    @Test
    fun getFavoriteNotes_reEmitsWhenAFavoriteIsRemoved() = runTest {
        noteDao.upsertNotes(listOf(note("a"), note("b")))

        noteDao.getFavoriteNotes().test {
            assertEquals(setOf("a", "b"), awaitItem().map { it.noteId }.toSet())

            noteDao.updateFavorite("a", isFavorite = false)

            assertEquals(listOf("b"), awaitItem().map { it.noteId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upsertNotes_thenGetFavoriteNotes_returnsOnlyFavorites() = runTest {
        noteDao.upsertNotes(
            listOf(
                note("fav1", isFavorite = true),
                note("fav2", isFavorite = true),
                note("notFav", isFavorite = false),
            )
        )

        val favorites = noteDao.getFavoriteNotes().first()

        assertEquals(2, favorites.size)
        assertTrue(favorites.all { it.isFavorite })
        assertFalse(favorites.any { it.noteId == "notFav" })
    }

    @Test
    fun getFavoriteNotes_areOrderedByCreatedDateDescending() = runTest {
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
    fun upsertNotes_replacesOnConflictByPrimaryKey() = runTest {
        noteDao.upsertNotes(listOf(note("doc", isFavorite = true).copy(text = "original")))
        noteDao.upsertNotes(listOf(note("doc", isFavorite = true).copy(text = "updated")))

        val favorites = noteDao.getFavoriteNotes().first()

        assertEquals(1, favorites.size)
        assertEquals("updated", favorites.first().text)
    }

    @Test
    fun deleteNote_removesMatchingNoteId() = runTest {
        noteDao.upsertNotes(listOf(note("keep"), note("remove")))

        noteDao.deleteNote("remove")

        val favorites = noteDao.getFavoriteNotes().first()
        assertEquals(1, favorites.size)
        assertEquals("keep", favorites.first().noteId)
    }

    @Test
    fun updateFavorite_toFalse_removesNoteFromFavorites() = runTest {
        noteDao.upsertNotes(listOf(note("doc", isFavorite = true)))

        noteDao.updateFavorite("doc", isFavorite = false)

        assertTrue(noteDao.getFavoriteNotes().first().isEmpty())
    }

    @Test
    fun clearFavorites_removesAllFavoriteNotes() = runTest {
        noteDao.upsertNotes(listOf(note("a"), note("b"), note("c")))

        noteDao.clearFavorites()

        assertTrue(noteDao.getFavoriteNotes().first().isEmpty())
    }

    @Test
    fun clearAllNotes_removesEveryNoteRegardlessOfFavoriteFlag() = runTest {
        noteDao.upsertNotes(listOf(note("fav", isFavorite = true), note("notFav", isFavorite = false)))

        noteDao.clearAllNotes()

        // getFavoriteNotes() alone can't distinguish this from clearFavorites(), since both leave
        // it empty -- getNotes() reads the whole table, so it confirms the non-favorite row is gone
        // too. (This counted rows through a SupportSQLite cursor, which exists only on Android.)
        assertTrue(noteDao.getNotes().first().isEmpty())
    }

    @Test
    fun syncFavoriteNotes_replacesExistingFavorites() = runTest {
        noteDao.upsertNotes(listOf(note("old1"), note("old2")))

        noteDao.syncFavoriteNotes(listOf(note("new1"), note("new2"), note("new3")))

        val favorites = noteDao.getFavoriteNotes().first()
        assertEquals(setOf("new1", "new2", "new3"), favorites.map { it.noteId }.toSet())
    }

    @Test
    fun mediaList_isPersistedAndRestoredViaTypeConverter() = runTest {
        val media = listOf(
            MediaDetail(
                photoUrl = "https://example.com/photo.jpg",
                generatedTexts = listOf("a cat"),
                generatedLabels = listOf(DetectedLabel("animal", 0.9)),
            )
        )
        noteDao.upsertNotes(listOf(note("doc", mediaList = media)))

        val restored = noteDao.getFavoriteNotes().first().first()

        assertEquals(1, restored.mediaList?.size)
        assertEquals("https://example.com/photo.jpg", restored.mediaList?.first()?.photoUrl)
        assertEquals(listOf("a cat"), restored.mediaList?.first()?.generatedTexts)
        assertEquals("animal", restored.mediaList?.first()?.generatedLabels?.first()?.label)
    }
}