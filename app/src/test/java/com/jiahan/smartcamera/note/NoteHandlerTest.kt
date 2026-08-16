package com.jiahan.smartcamera.note

import app.cash.turbine.test
import com.jiahan.smartcamera.domain.HomeNote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteHandlerTest {

    private val noteHandler = NoteHandler()

    // -------------------------------------------------------------------------
    // notifyNoteAdded
    // -------------------------------------------------------------------------

    @Test
    fun `notifyNoteAdded emits Unit on noteAddedEvent`() = runTest {
        noteHandler.noteAddedEvent.test {
            noteHandler.notifyNoteAdded()
            awaitItem() // just Unit – verifies emission occurred
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notifyNoteAdded emits event each time it is called`() = runTest {
        noteHandler.noteAddedEvent.test {
            noteHandler.notifyNoteAdded()
            noteHandler.notifyNoteAdded()
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // notifyNoteDeleted
    // -------------------------------------------------------------------------

    @Test
    fun `notifyNoteDeleted emits the document path`() = runTest {
        val noteId = "note1"
        noteHandler.noteDeletedEvent.test {
            noteHandler.notifyNoteDeleted(noteId)
            assertEquals(noteId, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notifyNoteDeleted emits all paths in order`() = runTest {
        noteHandler.noteDeletedEvent.test {
            noteHandler.notifyNoteDeleted("note1")
            noteHandler.notifyNoteDeleted("note2")
            assertEquals("note1", awaitItem())
            assertEquals("note2", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // notifyNoteFavorited
    // -------------------------------------------------------------------------

    @Test
    fun `notifyNoteFavorited emits the HomeNote`() = runTest {
        val note = HomeNote(
            noteId = "note1",
            text = "Test",
            username = "user1",
            favorite = true
        )
        noteHandler.noteFavoritedEvent.test {
            noteHandler.notifyNoteFavorited(note)
            assertEquals(note, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notifyNoteFavorited preserves favorite flag`() = runTest {
        val favorited = HomeNote(noteId = "note1", username = "u", favorite = true)
        val unfavorited = HomeNote(noteId = "note2", username = "u", favorite = false)

        noteHandler.noteFavoritedEvent.test {
            noteHandler.notifyNoteFavorited(favorited)
            noteHandler.notifyNoteFavorited(unfavorited)
            assertEquals(true, awaitItem().favorite)
            assertEquals(false, awaitItem().favorite)
            cancelAndIgnoreRemainingEvents()
        }
    }
}