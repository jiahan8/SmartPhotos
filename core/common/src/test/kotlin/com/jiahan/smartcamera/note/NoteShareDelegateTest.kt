package com.jiahan.smartcamera.note

import android.net.Uri
import app.cash.turbine.test
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delegate came down here with `:feature:*` extraction in mind, and arrived with no direct
 * coverage of its own -- four ViewModel suites exercised it only as far as "shareNote was called".
 * These pin the behaviour that is actually in it: which url a media item resolves to, that a
 * partial download still shares, and that a total one reports instead.
 *
 * mockk rather than `:core:testing`'s fakes, which this module cannot reach without a cycle.
 */
class NoteShareDelegateTest {

    private val mediaFileRepository: MediaFileRepository = mockk()
    private val errorHandler: ErrorHandler = mockk(relaxed = true)
    private val resourceProvider: ResourceProvider = mockk()
    private val noteErrorReporter = NoteErrorReporter(errorHandler)

    private val delegate =
        NoteShareDelegate(mediaFileRepository, noteErrorReporter, resourceProvider)

    private fun note(text: String? = "a note", media: List<MediaDetail>? = null) =
        HomeNote(noteId = "n1", text = text, username = "tester", mediaList = media)

    private fun photo(url: String) = MediaDetail(photoUrl = url, isVideo = false)
    private fun video(url: String) = MediaDetail(videoUrl = url, isVideo = true)

    private fun uri(): Uri = mockk()

    @Test
    fun `a text-only note shares with no uris`() = runTest {
        delegate.shareEvent.test {
            delegate.shareNote(note(text = "just words"))
            val content = awaitItem()
            assertEquals("just words", content.text)
            assertTrue(content.uris.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every downloaded media file reaches the share content`() = runTest {
        val first = uri()
        val second = uri()
        coEvery { mediaFileRepository.downloadToCacheFile("a", false) } returns first
        coEvery { mediaFileRepository.downloadToCacheFile("b", false) } returns second

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(photo("a"), photo("b"))))
            assertEquals(listOf(first, second), awaitItem().uris)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a video resolves its videoUrl and a photo its photoUrl`() = runTest {
        val downloaded = uri()
        coEvery { mediaFileRepository.downloadToCacheFile("clip", true) } returns downloaded
        coEvery { mediaFileRepository.downloadToCacheFile("still", false) } returns downloaded

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(video("clip"), photo("still"))))
            // Both resolved, which is only true if isVideo picked the right field on each.
            assertEquals(2, awaitItem().uris.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `one failed download still shares the rest`() = runTest {
        val ok = uri()
        coEvery { mediaFileRepository.downloadToCacheFile("good", false) } returns ok
        coEvery { mediaFileRepository.downloadToCacheFile("bad", false) } returns null

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(photo("good"), photo("bad"))))
            // Partial is not failure: sharing one of two photos beats sharing nothing.
            assertEquals(listOf(ok), awaitItem().uris)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a note whose media all fail reports instead of sharing`() = runTest {
        coEvery { mediaFileRepository.downloadToCacheFile(any(), any()) } returns null
        every { resourceProvider.getString(any()) } returns "Couldn't share this note."
        every { errorHandler.logError(any(), any()) } just runs

        noteErrorReporter.actionError.test {
            delegate.shareNote(note(media = listOf(photo("a"), photo("b"))))
            assertEquals("Couldn't share this note.", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a note whose media all fail emits no share event`() = runTest {
        coEvery { mediaFileRepository.downloadToCacheFile(any(), any()) } returns null
        every { resourceProvider.getString(any()) } returns "failed"

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(photo("a"))))
            // The chooser must not open on an empty share.
            expectNoEvents()
        }
    }
}