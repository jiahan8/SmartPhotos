package com.jiahan.smartcamera.note

import app.cash.turbine.test
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [NoteErrorReporter] had no suite while it sat in :core:common: four ViewModel suites read its
 * flow, and `NoteShareDelegateTest` its share-failure case. Moving it to `commonMain` is the moment
 * to pin the other half, which every note screen's snackbar depends on -- that a reported failure is
 * both logged and emitted, as an identity rather than text.
 */
class NoteErrorReporterTest {

    private val logged = mutableListOf<Throwable>()

    private val reporter = NoteErrorReporter(
        object : ErrorHandler {
            override fun logError(throwable: Throwable, tag: String) {
                logged += throwable
            }
        }
    )

    @Test
    fun `reportError logs the failure and emits its identity`() = runTest {
        val failure = RuntimeException("boom")

        reporter.actionError.test {
            reporter.reportError(failure)
            assertEquals(NoteActionError.Failed(ErrorMessage.Unlocalized("boom")), awaitItem())
        }
        assertEquals(listOf<Throwable>(failure), logged)
    }

    /** Nothing threw -- every download came back empty -- so there is nothing to log. */
    @Test
    fun `reportShareFailure emits ShareFailed without logging`() = runTest {
        reporter.actionError.test {
            reporter.reportShareFailure()
            assertEquals(NoteActionError.ShareFailed, awaitItem())
        }
        assertTrue(logged.isEmpty())
    }
}