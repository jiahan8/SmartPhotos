package com.jiahan.smartcamera.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.data.DatabaseNote
import com.jiahan.smartcamera.database.data.toDatabaseNote
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.DefaultErrorHandler
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProviderImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the failure branches of [DefaultNoteRepository] that raise an error of their own rather
 * than surfacing one from Firestore.
 *
 * These assert the string a user would actually see — the failure resolved through a real
 * [DefaultErrorHandler], exactly as a ViewModel resolves it — rather than the exception type or
 * its message. That is the contract that must not change; how the repository encodes the failure
 * internally is free to.
 *
 * Runs under Robolectric for [Tasks] and for real string resources.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultNoteRepositoryTest {

    private companion object {
        const val USER_ID = "user-1"
        const val AUTHOR_ID = "author-1"
        const val NOTE_ID = "note-1"

        const val COLLECTION_USER = "user"
        const val COLLECTION_NOTE = "note"
        const val FIELD_USER_ID = "user_id"
        const val FIELD_TEXT = "text"
        const val FIELD_CREATED = "created"
        const val FIELD_FAVORITE = "favorite"
        const val FIELD_MEDIA_LIST = "media_list"
        const val FIELD_USERNAME = "username"
        const val FIELD_PROFILE_PICTURE = "profile_picture"
    }

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val authRepository: AuthRepository = mockk()
    private val firestore: FirebaseFirestore = mockk()
    private val remoteConfigRepository: RemoteConfigRepository = mockk(relaxed = true)
    private val functions: FirebaseFunctions = mockk(relaxed = true)
    private val noteDao: NoteDao = mockk(relaxed = true)
    private val mediaFileRepository: MediaFileRepository = mockk(relaxed = true)
    private val errorHandler: ErrorHandler = mockk(relaxed = true)

    private val userCollection: CollectionReference = mockk()
    private val currentUserDocument: DocumentReference = mockk()
    private val noteCollection: CollectionReference = mockk()
    private val noteDocumentRef: DocumentReference = mockk()
    private val authorDocumentRef: DocumentReference = mockk()
    private val noteSnapshot: DocumentSnapshot = mockk()
    private val authorSnapshot: DocumentSnapshot = mockk()
    private val feedQuery: Query = mockk()
    private val limitedQuery: Query = mockk()
    private val feedSnapshot: QuerySnapshot = mockk()

    private val dispatcher = UnconfinedTestDispatcher()

    private val repository = DefaultNoteRepository(
        context = context,
        remoteConfigRepository = remoteConfigRepository,
        authRepository = authRepository,
        firestore = firestore,
        functions = functions,
        noteDao = noteDao,
        mediaFileRepository = mediaFileRepository,
        errorHandler = errorHandler,
        // SupervisorJob mirrors di/AppModule's @ApplicationScope: without it a failing child of
        // quickUploadMediaToFirebase would cancel its siblings and the scope, a failure mode
        // production does not have.
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        ioDispatcher = dispatcher,
    )

    /** Resolves a failure to the string a user would see, the way every ViewModel does. */
    private val messageResolver: ErrorHandler =
        DefaultErrorHandler(ResourceProviderImpl(context))

    private fun userFacingMessage(result: Result<*>): String =
        messageResolver.getErrorMessage(result.exceptionOrNull()!!)

    @Before
    fun setUp() {
        every { authRepository.currentUserId } returns USER_ID

        // noteCollectionReference: user/{uid}/note
        every { firestore.collection(COLLECTION_USER) } returns userCollection
        every { userCollection.document(USER_ID) } returns currentUserDocument
        every { currentUserDocument.collection(COLLECTION_NOTE) } returns noteCollection
        every { noteCollection.document(NOTE_ID) } returns noteDocumentRef
        every { noteDocumentRef.get() } returns Tasks.forResult(noteSnapshot)

        // getUserDocumentSnapshot(authorId): user/{authorId}
        every { userCollection.document(AUTHOR_ID) } returns authorDocumentRef
        every { authorDocumentRef.get() } returns Tasks.forResult(authorSnapshot)

        // A note that resolves cleanly; individual tests narrow one field to force a branch.
        every { noteSnapshot.exists() } returns true
        every { noteSnapshot.id } returns NOTE_ID
        every { noteSnapshot.getString(FIELD_USER_ID) } returns AUTHOR_ID
        every { noteSnapshot.getString(FIELD_TEXT) } returns "hello"
        every { noteSnapshot.getDate(FIELD_CREATED) } returns null
        every { noteSnapshot.getBoolean(FIELD_FAVORITE) } returns false
        every { noteSnapshot.get(FIELD_MEDIA_LIST) } returns null

        every { authorSnapshot.exists() } returns true
        every { authorSnapshot.getString(FIELD_USERNAME) } returns "alice"
        every { authorSnapshot.getString(FIELD_PROFILE_PICTURE) } returns null

        // getNotes: user/{uid}/note orderBy(created).limit(pageSize), returning the one note above.
        every { noteCollection.orderBy(FIELD_CREATED, Query.Direction.DESCENDING) } returns feedQuery
        every { feedQuery.limit(any()) } returns limitedQuery
        every { limitedQuery.get() } returns Tasks.forResult(feedSnapshot)
        every { feedSnapshot.documents } returns listOf(noteSnapshot)
    }

    /** Stubs the Cloud Function call the write paths delegate to, so `.await()` completes. */
    /**
     * A real [HttpsCallableResult], because mockk cannot stub its `data`: the getter is final and
     * Robolectric's classloader defeats the inline instrumentation that would normally handle that.
     * The class has no public constructor, so this reaches for the single-argument one Firebase
     * declares. If a Firebase upgrade ever changes that signature this fails loudly here rather
     * than silently skipping the assertion.
     */
    private fun callableResult(data: Any?): HttpsCallableResult =
        HttpsCallableResult::class.java
            .getDeclaredConstructor(Any::class.java)
            .apply { isAccessible = true }
            .newInstance(data)

    private fun stubCallable(result: HttpsCallableResult = mockk()) {
        val callable: HttpsCallableReference = mockk()
        every { callable.call(any()) } returns Tasks.forResult(result)
        every { functions.getHttpsCallable(any()) } returns callable
    }

    private fun captureCachedNotes(): List<DatabaseNote> {
        val cached = slot<List<DatabaseNote>>()
        coVerify { noteDao.upsertNotes(capture(cached)) }
        return cached.captured
    }

    // -------------------------------------------------------------------------
    // getNote
    // -------------------------------------------------------------------------

    @Test
    fun `getNote signed out fails with the not-signed-in message`() = runTest(dispatcher) {
        every { authRepository.currentUserId } returns null

        val result = repository.getNote(NOTE_ID)

        assertTrue(result.isFailure)
        assertEquals(
            context.getString(R.string.user_not_authenticated),
            userFacingMessage(result)
        )
    }

    @Test
    fun `getNote missing note document fails with the note-unavailable message`() =
        runTest(dispatcher) {
            every { noteSnapshot.exists() } returns false

            val result = repository.getNote(NOTE_ID)

            assertTrue(result.isFailure)
            assertEquals(
                context.getString(R.string.note_unavailable),
                userFacingMessage(result)
            )
        }

    @Test
    fun `getNote note without an author id fails with the note-unavailable message`() =
        runTest(dispatcher) {
            every { noteSnapshot.getString(FIELD_USER_ID) } returns null

            val result = repository.getNote(NOTE_ID)

            assertTrue(result.isFailure)
            assertEquals(
                context.getString(R.string.note_unavailable),
                userFacingMessage(result)
            )
        }

    @Test
    fun `getNote missing author document fails with the note-unavailable message`() =
        runTest(dispatcher) {
            every { authorSnapshot.exists() } returns false

            val result = repository.getNote(NOTE_ID)

            assertTrue(result.isFailure)
            assertEquals(
                context.getString(R.string.note_unavailable),
                userFacingMessage(result)
            )
        }

    /** Guards the success path against the failure branches above being widened by accident. */
    @Test
    fun `getNote resolves the note and its author`() = runTest(dispatcher) {
        val result = repository.getNote(NOTE_ID)

        assertTrue(result.isSuccess)
        val note = result.getOrThrow()
        assertEquals(NOTE_ID, note.noteId)
        assertEquals("hello", note.text)
        assertEquals("alice", note.username)
        assertNull(note.profilePictureUrl)
    }

    // -------------------------------------------------------------------------
    // uploadMediaToFirebase
    // -------------------------------------------------------------------------

    @Test
    fun `uploadMediaToFirebase signed out fails with the not-signed-in message`() =
        runTest(dispatcher) {
            every { authRepository.currentUserId } returns null

            val result = repository.uploadMediaToFirebase(
                listOf(NoteMediaDetail(photoUri = MediaUri("file:///tmp/photo.jpg")))
            )

            assertTrue(result.isFailure)
            assertEquals(
                context.getString(R.string.user_not_authenticated),
                userFacingMessage(result)
            )
        }

    // -------------------------------------------------------------------------
    // The local mirror
    //
    // Room used to hold favorited notes only -- every write was gated on the flag and
    // unfavoriting deleted the row. It mirrors the whole feed now, which is the prerequisite for
    // Home and Search observing a live query instead of exchanging NoteHandler events. These pin
    // the three writes that changed.
    // -------------------------------------------------------------------------

    @Test
    fun `getNotes mirrors the fetched page into Room`() = runTest(dispatcher) {
        val result = repository.getNotes()

        assertTrue(result.isSuccess)
        val cached = captureCachedNotes()
        assertEquals(1, cached.size)
        assertEquals(NOTE_ID, cached.single().noteId)
        // The note is not favorited: before this change nothing would have been written at all.
        assertFalse(cached.single().favorite)
    }

    @Test
    fun `getNotes fails when the mirror write fails`() = runTest(dispatcher) {
        coEvery { noteDao.upsertNotes(any()) } throws RuntimeException("room is gone")

        val result = repository.getNotes()

        // This used to succeed, back when nothing read the table for the feed and blanking a
        // screen over a failed cache write was the worse trade. Home renders the mirror now, so a
        // page that did not land is a page the user cannot see -- and reporting success would let
        // the caller advance its cursor past it, making the hole permanent.
        assertTrue(result.isFailure)
    }

    @Test
    fun `favoriteNote keeps the row when a note is unfavorited`() = runTest(dispatcher) {
        every { noteDocumentRef.update(FIELD_FAVORITE, any()) } returns Tasks.forResult(null)

        val result = repository.favoriteNote(homeNote(favorite = true))

        assertTrue(result.isSuccess)
        // Not noteDao.deleteNote: the note still exists, it is just no longer favorited.
        coVerify(exactly = 0) { noteDao.deleteNote(any()) }
        val cached = captureCachedNotes()
        assertEquals(NOTE_ID, cached.single().noteId)
        assertFalse(cached.single().favorite)
    }

    @Test
    fun `updateNote caches a note that is not favorited`() = runTest(dispatcher) {
        stubCallable()

        val result = repository.updateNote(homeNote(favorite = false).copy(text = "edited"))

        assertTrue(result.isSuccess)
        val cached = captureCachedNotes()
        assertEquals("edited", cached.single().text)
    }

    @Test
    fun `getNotesStream maps the mirrored rows to domain notes`() = runTest(dispatcher) {
        every { noteDao.getNotes(any<Int>()) } returns flowOf(
            listOf(homeNote(favorite = false).toDatabaseNote())
        )

        val notes = repository.getNotesStream(limit = 10).first()

        assertEquals(NOTE_ID, notes.single().noteId)
        assertEquals("alice", notes.single().username)
        // The query itself -- that it returns non-favorites, newest first, and re-emits on a write
        // -- is NoteDaoTest's job, against a real database. This pins only the mapping.
        assertFalse(notes.single().favorite)
    }

    @Test
    fun `getNotesStream passes the window straight through to the DAO`() = runTest(dispatcher) {
        every { noteDao.getNotes(any<Int>()) } returns flowOf(emptyList())

        repository.getNotesStream(limit = 30).first()

        // The feed's window is the query's LIMIT: the repository holds no pagination state of its
        // own, so a caller asking for 30 must not silently get the whole table.
        verify { noteDao.getNotes(30) }
    }

    @Test
    fun `getNoteStream maps the row and emits null once it is gone`() = runTest(dispatcher) {
        every { noteDao.getNote(NOTE_ID) } returns
                flowOf(homeNote(favorite = true).toDatabaseNote())
        assertEquals(NOTE_ID, repository.getNoteStream(NOTE_ID).first()?.noteId)

        every { noteDao.getNote(NOTE_ID) } returns flowOf(null)
        assertNull(repository.getNoteStream(NOTE_ID).first())
    }

    @Test
    fun `searchNotesStream filters the mirror by text`() = runTest(dispatcher) {
        every { noteDao.getNotes() } returns flowOf(
            listOf(
                homeNote(favorite = false).copy(noteId = "a", text = "grocery list")
                    .toDatabaseNote(),
                homeNote(favorite = false).copy(noteId = "b", text = "meeting notes")
                    .toDatabaseNote()
            )
        )

        val results = repository.searchNotesStream("grocery").first()

        assertEquals("a", results.single().noteId)
    }

    @Test
    fun `searchNotes mirrors its results into Room`() = runTest(dispatcher) {
        // searchNotes reads the collection unpaged, so it takes the un-limited query.
        every { feedQuery.get() } returns Tasks.forResult(feedSnapshot)

        val result = repository.searchNotes(query = "")

        // Search reads the whole collection remotely; writing the results through is what keeps
        // searchNotesStream from narrowing to only what the feed has paged.
        assertTrue(result.isSuccess)
        assertEquals(NOTE_ID, captureCachedNotes().single().noteId)
    }

    @Test
    fun `addNote reads the created note back into the mirror`() = runTest(dispatcher) {
        stubCallable(callableResult(mapOf("documentPath" to NOTE_ID)))

        val result = repository.addNote(homeNote(favorite = false).copy(noteId = ""))

        // The client cannot build the created row itself -- the id and `created` are stamped
        // server-side -- so addNote reads it back. That read is what retired NoteHandler.
        assertTrue(result.isSuccess)
        assertEquals(NOTE_ID, captureCachedNotes().single().noteId)
    }

    @Test
    fun `addNote succeeds even when the read-back fails`() = runTest(dispatcher) {
        stubCallable(callableResult(mapOf("documentPath" to NOTE_ID)))
        every { noteSnapshot.exists() } returns false

        val result = repository.addNote(homeNote(favorite = false).copy(noteId = ""))

        // The note was created. Reporting a write that succeeded as an error because the follow-up
        // read failed would be worse than the note arriving on the next refresh.
        assertTrue(result.isSuccess)
        verify { errorHandler.logError(any(), any()) }
    }

    @Test
    fun `addNote logs when the function returns no document path`() = runTest(dispatcher) {
        stubCallable()

        val result = repository.addNote(homeNote(favorite = false).copy(noteId = ""))

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { noteDao.upsertNotes(any()) }
    }

    @Test
    fun `getNote mirrors the fetched note into Room`() = runTest(dispatcher) {
        val result = repository.getNote(NOTE_ID)

        assertTrue(result.isSuccess)
        assertEquals(NOTE_ID, captureCachedNotes().single().noteId)
    }

    private fun homeNote(favorite: Boolean) = HomeNote(
        noteId = NOTE_ID,
        text = "hello",
        createdDate = null,
        favorite = favorite,
        mediaList = null,
        username = "alice",
        profilePictureUrl = null,
    )
}