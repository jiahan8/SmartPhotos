package com.jiahan.smartcamera.note

import app.cash.turbine.test
import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.util.ErrorHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The delegate came down to :core:common with `:feature:*` extraction in mind, and arrived with no
 * direct coverage of its own -- four ViewModel suites exercised it only as far as "shareNote was
 * called". These pin the behaviour that is actually in it: which url a media item resolves to, that
 * a partial download still shares, and that a total one reports instead.
 *
 * It has since followed its subject into :core:domain's `commonTest`. The mocked `android.net.Uri`s
 * became plain [MediaUri] values and the mocked repository a lookup table, declared here rather
 * than taken from :core:domain-testing's `FakeMediaCacheRepository`: that fake answers every url
 * alike, and the cases below need some to succeed and some to fail.
 */
class NoteShareDelegateTest {

    /** Downloads by lookup: a url and `isVideo` pair missing from [downloads] fails. */
    private class LookupMediaCacheRepository(
        private val downloads: Map<Pair<String, Boolean>, MediaUri>
    ) : MediaCacheRepository {
        override suspend fun downloadToCacheFile(url: String, isVideo: Boolean): MediaUri? =
            downloads[url to isVideo]
    }

    private val noteErrorReporter = NoteErrorReporter(
        object : ErrorHandler {
            override fun logError(throwable: Throwable, tag: String) = Unit
        }
    )

    private fun delegate(downloads: Map<Pair<String, Boolean>, MediaUri> = emptyMap()) =
        NoteShareDelegate(LookupMediaCacheRepository(downloads), noteErrorReporter)

    private fun note(text: String? = "a note", media: List<MediaDetail>? = null) =
        Note(noteId = "n1", text = text, username = "tester", mediaList = media)

    private fun photo(url: String) = MediaDetail(photoUrl = url, isVideo = false)
    private fun video(url: String) = MediaDetail(videoUrl = url, isVideo = true)

    private fun cached(name: String) =
        MediaUri("content://com.jiahan.smartcamera.fileprovider/cache/$name")

    @Test
    fun `a text-only note shares with no uris`() = runTest {
        val delegate = delegate()

        delegate.shareEvent.test {
            delegate.shareNote(note(text = "just words"))
            assertEquals(OutgoingShare(text = "just words", uris = emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every downloaded media file reaches the share content`() = runTest {
        val delegate = delegate(
            mapOf(("a" to false) to cached("a"), ("b" to false) to cached("b"))
        )

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(photo("a"), photo("b"))))
            assertEquals(listOf(cached("a"), cached("b")), awaitItem().uris)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a video resolves its videoUrl and a photo its photoUrl`() = runTest {
        val delegate = delegate(
            mapOf(("clip" to true) to cached("clip"), ("still" to false) to cached("still"))
        )

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(video("clip"), photo("still"))))
            // Both resolved, which is only true if isVideo picked the right field on each.
            assertEquals(listOf(cached("clip"), cached("still")), awaitItem().uris)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `one failed download still shares the rest`() = runTest {
        val delegate = delegate(mapOf(("good" to false) to cached("good")))

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(photo("good"), photo("bad"))))
            // Partial is not failure: sharing one of two photos beats sharing nothing.
            assertEquals(listOf(cached("good")), awaitItem().uris)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a note whose media all fail reports instead of sharing`() = runTest {
        val delegate = delegate()

        noteErrorReporter.actionError.test {
            delegate.shareNote(note(media = listOf(photo("a"), photo("b"))))
            assertEquals(NoteActionError.ShareFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a note whose media all fail emits no share event`() = runTest {
        val delegate = delegate()

        delegate.shareEvent.test {
            delegate.shareNote(note(media = listOf(photo("a"))))
            // The chooser must not open on an empty share.
            expectNoEvents()
        }
    }
}