package com.jiahan.smartcamera.note

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Only [NoteHandler.noteAddedEvent] is left to test. The deleted/favorited/updated events went with
 * the move onto the Room mirror -- those mutations are table writes now, and the tests that covered
 * them moved to the ViewModels that observe the table.
 */
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
}