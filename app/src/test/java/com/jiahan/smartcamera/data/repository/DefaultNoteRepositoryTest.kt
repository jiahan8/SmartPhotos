package com.jiahan.smartcamera.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.DefaultErrorHandler
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProviderImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}