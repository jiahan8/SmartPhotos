package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.data.DatabaseNote
import com.jiahan.smartcamera.database.data.toDatabaseNote
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import dev.gitlive.firebase.internal.decode
import dev.gitlive.firebase.internal.encode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.nullable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [DefaultNoteRepository]: the failures it raises itself, the Room mirror it writes every
 * fetch into, the page cursor, and the readers and writers between Firebase and a [Note].
 *
 * The failure tests assert the [AppError] identity, which is the whole of this layer's contract for a
 * failure it raises itself: :core:common's `appErrorMessageResId` turns it into a string, pinned there
 * by `ErrorMessagesTest`.
 *
 * Firebase sits behind [NoteStore] and [NoteCallable]. Their fakes hold raw field maps and run them
 * through GitLive's own `decode` -- what a snapshot's `data` and a callable's result call -- so the
 * decoders are under test and only the network is faked. The callable arguments are checked through
 * GitLive's `encode` the same way. Room is [FakeNoteDao]: the queries themselves are `NoteDaoTest`'s,
 * against a real database, and this pins only what the repository writes and how it maps what it
 * reads.
 */
class DefaultNoteRepositoryTest {

    private companion object {
        const val USER_ID = "user-1"
        const val AUTHOR_ID = "author-1"
        const val NOTE_ID = "note-1"
    }

    private class FakePosition(val noteId: String) : NotePosition

    /** One [NoteStore.getNotes] call, with its position reduced to the note it names. */
    private data class NotesQuery(
        val userId: String,
        val limit: Int?,
        val startAfterId: String?,
        val favoritesOnly: Boolean,
    )

    /**
     * One account's Firestore: `user/{ownerId}/note`, newest first, and the `user` documents authors
     * resolve against. Another account's collection reads as empty.
     */
    private class FakeNoteStore(private val ownerId: String) : NoteStore {
        val notes = linkedMapOf<String, Map<String, Any?>>()
        val authors = mutableMapOf<String, Map<String, Any?>>()
        val failingAuthorIds = mutableSetOf<String>()

        val queries = mutableListOf<NotesQuery>()
        val deletedIds = mutableListOf<String>()
        val favoriteWrites = mutableListOf<Pair<String, Boolean>>()

        override suspend fun getNotes(
            userId: String,
            limit: Int?,
            startAfter: NotePosition?,
            favoritesOnly: Boolean,
        ): List<NoteDocument> {
            val startAfterId = (startAfter as FakePosition?)?.noteId
            queries += NotesQuery(userId, limit, startAfterId, favoritesOnly)
            if (userId != ownerId) return emptyList()
            val ids = notes.keys.toList()
            return ids.drop(startAfterId?.let { ids.indexOf(it) + 1 } ?: 0)
                .filter { !favoritesOnly || notes.getValue(it)["favorite"] == true }
                .take(limit ?: ids.size)
                .map(::document)
        }

        override suspend fun getNote(userId: String, noteId: String): NoteDocument? =
            if (userId == ownerId && noteId in notes) document(noteId) else null

        override suspend fun getAuthor(userId: String): FirestoreAuthor? {
            if (userId in failingAuthorIds) throw IllegalStateException("author lookup failed")
            return authors[userId]?.let { decode(FirestoreAuthor.serializer(), it) }
        }

        override suspend fun deleteNote(userId: String, noteId: String) {
            deletedIds += noteId
        }

        override suspend fun setFavorite(userId: String, noteId: String, isFavorite: Boolean) {
            favoriteWrites += noteId to isFavorite
        }

        private fun document(id: String) = NoteDocument(
            id = id,
            fields = decode(FirestoreNote.serializer(), notes.getValue(id)),
            position = FakePosition(id),
        )
    }

    private class FakeNoteCallable : NoteCallable {
        val names = mutableListOf<String>()
        val createArgs = mutableListOf<CreateNoteArgs>()
        val updateArgs = mutableListOf<UpdateNoteArgs>()

        /** createNote's raw reply. */
        var createPayload: Any? = null

        override suspend fun createNote(name: String, args: CreateNoteArgs): CreateNoteResult? {
            names += name
            createArgs += args
            return decode(CreateNoteResult.serializer().nullable, createPayload)
        }

        override suspend fun updateNote(name: String, args: UpdateNoteArgs) {
            names += name
            updateArgs += args
        }
    }

    /** The mirror as a plain list, recording what the repository writes and asks for. */
    private class FakeNoteDao : NoteDao {
        val rows = MutableStateFlow<List<DatabaseNote>>(emptyList())
        val upserts = mutableListOf<List<DatabaseNote>>()
        val deletedIds = mutableListOf<String>()
        val requestedLimits = mutableListOf<Int>()
        var upsertFailure: Throwable? = null

        override fun getNotes(): Flow<List<DatabaseNote>> = rows

        override fun getNotes(limit: Int): Flow<List<DatabaseNote>> {
            requestedLimits += limit
            return rows.map { it.take(limit) }
        }

        override fun getNote(noteId: String): Flow<DatabaseNote?> =
            rows.map { notes -> notes.find { it.noteId == noteId } }

        override fun getFavoriteNotes(): Flow<List<DatabaseNote>> =
            rows.map { notes -> notes.filter { it.isFavorite } }

        override suspend fun upsertNotes(notes: List<DatabaseNote>) {
            upsertFailure?.let { throw it }
            upserts += notes
            val ids = notes.map { it.noteId }.toSet()
            rows.update { current -> current.filterNot { it.noteId in ids } + notes }
        }

        override suspend fun deleteNote(noteId: String) {
            deletedIds += noteId
            rows.update { current -> current.filterNot { it.noteId == noteId } }
        }

        override suspend fun updateFavorite(noteId: String, isFavorite: Boolean) {
            rows.update { current ->
                current.map { if (it.noteId == noteId) it.copy(isFavorite = isFavorite) else it }
            }
        }

        override suspend fun clearFavorites() {
            rows.update { current -> current.filterNot { it.isFavorite } }
        }

        override suspend fun clearAllNotes() {
            rows.value = emptyList()
        }
    }

    private val authRepository = FakeAuthRepository().apply { currentUserId = USER_ID }
    private val store = FakeNoteStore(ownerId = USER_ID)
    private val callable = FakeNoteCallable()
    private val noteDao = FakeNoteDao()
    private val errorHandler = FakeErrorHandler()

    private val repository = DefaultNoteRepository(
        authRepository = authRepository,
        store = store,
        callable = callable,
        noteDao = noteDao,
        errorHandler = errorHandler,
    )

    init {
        // A note that resolves cleanly; individual tests narrow one field to force a branch.
        store.notes[NOTE_ID] = noteFields()
        store.authors[AUTHOR_ID] = mapOf("username" to "alice")
    }

    // -------------------------------------------------------------------------
    // getNote
    // -------------------------------------------------------------------------

    @Test
    fun `getNote signed out fails as NotAuthenticated`() = runTest {
        authRepository.currentUserId = null

        val result = repository.getNote(NOTE_ID)

        assertIs<AppError.NotAuthenticated>(result.exceptionOrNull())
    }

    @Test
    fun `getNote missing note document fails as NoteUnavailable`() = runTest {
        store.notes.clear()

        val result = repository.getNote(NOTE_ID)

        assertIs<AppError.NoteUnavailable>(result.exceptionOrNull())
    }

    @Test
    fun `getNote note without an author id fails as NoteUnavailable`() = runTest {
        store.notes[NOTE_ID] = noteFields(userId = null)

        val result = repository.getNote(NOTE_ID)

        assertIs<AppError.NoteUnavailable>(result.exceptionOrNull())
    }

    @Test
    fun `getNote missing author document fails as NoteUnavailable`() = runTest {
        store.authors.clear()

        val result = repository.getNote(NOTE_ID)

        assertIs<AppError.NoteUnavailable>(result.exceptionOrNull())
    }

    /** Guards the success path against the failure branches above being widened by accident. */
    @Test
    fun `getNote resolves the note and its author`() = runTest {
        val result = repository.getNote(NOTE_ID)

        assertTrue(result.isSuccess)
        val note = result.getOrThrow()
        assertEquals(NOTE_ID, note.noteId)
        assertEquals("hello", note.text)
        assertEquals("alice", note.username)
        assertNull(note.profilePictureUrl)
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
    fun `getNotes mirrors the fetched page into Room`() = runTest {
        val result = repository.getNotes()

        assertTrue(result.isSuccess)
        val cached = noteDao.upserts.single()
        assertEquals(1, cached.size)
        assertEquals(NOTE_ID, cached.single().noteId)
        // The note is not favorited: before this change nothing would have been written at all.
        assertFalse(cached.single().isFavorite)
    }

    @Test
    fun `getNotes fails when the mirror write fails`() = runTest {
        noteDao.upsertFailure = RuntimeException("room is gone")

        val result = repository.getNotes()

        // This used to succeed, back when nothing read the table for the feed and blanking a
        // screen over a failed cache write was the worse trade. Home renders the mirror now, so a
        // page that did not land is a page the user cannot see -- and reporting success would let
        // the caller advance its cursor past it, making the hole permanent.
        assertTrue(result.isFailure)
    }

    @Test
    fun `toggleFavorite keeps the row when a note is unfavorited`() = runTest {
        val result = repository.toggleFavorite(makeNote(isFavorite = true))

        assertTrue(result.isSuccess)
        assertEquals(listOf(NOTE_ID to false), store.favoriteWrites)
        // Not noteDao.deleteNote: the note still exists, it is just no longer favorited.
        assertTrue(noteDao.deletedIds.isEmpty())
        val cached = noteDao.upserts.single()
        assertEquals(NOTE_ID, cached.single().noteId)
        assertFalse(cached.single().isFavorite)
    }

    @Test
    fun `updateNote caches a note that is not favorited`() = runTest {
        val result = repository.updateNote(makeNote(isFavorite = false).copy(text = "edited"))

        assertTrue(result.isSuccess)
        assertEquals("edited", noteDao.upserts.single().single().text)
    }

    @Test
    fun `getNotesStream maps the mirrored rows to domain notes`() = runTest {
        noteDao.rows.value = listOf(makeNote(isFavorite = false).toDatabaseNote())

        val notes = repository.getNotesStream(limit = 10).first()

        assertEquals(NOTE_ID, notes.single().noteId)
        assertEquals("alice", notes.single().username)
        // The query itself -- that it returns non-favorites, newest first, and re-emits on a write
        // -- is NoteDaoTest's job, against a real database. This pins only the mapping.
        assertFalse(notes.single().isFavorite)
    }

    @Test
    fun `getNotesStream passes the window straight through to the DAO`() = runTest {
        repository.getNotesStream(limit = 30).first()

        // The feed's window is the query's LIMIT: the repository holds no pagination state of its
        // own, so a caller asking for 30 must not silently get the whole table.
        assertEquals(listOf(30), noteDao.requestedLimits)
    }

    @Test
    fun `getNoteStream maps the row and emits null once it is gone`() = runTest {
        noteDao.rows.value = listOf(makeNote(isFavorite = true).toDatabaseNote())
        assertEquals(NOTE_ID, repository.getNoteStream(NOTE_ID).first()?.noteId)

        noteDao.rows.value = emptyList()
        assertNull(repository.getNoteStream(NOTE_ID).first())
    }

    @Test
    fun `searchNotesStream filters the mirror by text`() = runTest {
        noteDao.rows.value = listOf(
            makeNote(isFavorite = false).copy(noteId = "a", text = "grocery list")
                .toDatabaseNote(),
            makeNote(isFavorite = false).copy(noteId = "b", text = "meeting notes")
                .toDatabaseNote()
        )

        val results = repository.searchNotesStream("grocery").first()

        assertEquals("a", results.single().noteId)
    }

    @Test
    fun `getFavoriteNotesStream filters the mirror by text`() = runTest {
        noteDao.rows.value = favoriteRows()

        val results = repository.getFavoriteNotesStream("grocery").first()

        assertEquals("a", results.single().noteId)
    }

    @Test
    fun `getFavoriteNotesStream treats a whitespace-only query as no query`() = runTest {
        noteDao.rows.value = favoriteRows()

        val results = repository.getFavoriteNotesStream("   ").first()

        // Blank, not empty. FavoriteViewModel passes the search field through untrimmed, and
        // the screen picks its empty-state copy with isBlank, so a query of spaces has to mean
        // "no query" here too. Checking isEmpty instead sent "   " into matchesQuery as a
        // literal substring, which here matches nothing -- the screen then rendered "favorite
        // a note to see it here" over a mirror that was full of favorites.
        assertEquals(listOf("a", "b"), results.map { it.noteId })
    }

    @Test
    fun `searchNotes mirrors its results into Room`() = runTest {
        val result = repository.searchNotes(query = "")

        // Search reads the whole collection remotely -- no limit -- and writing the results through
        // is what keeps searchNotesStream from narrowing to only what the feed has paged.
        assertTrue(result.isSuccess)
        assertNull(store.queries.single().limit)
        assertEquals(NOTE_ID, noteDao.upserts.single().single().noteId)
    }

    @Test
    fun `addNote reads the created note back into the mirror`() = runTest {
        callable.createPayload = mapOf("documentPath" to NOTE_ID)

        val result = repository.addNote(makeNote(isFavorite = false).copy(noteId = ""))

        // The client cannot build the created row itself -- the id and `created` are stamped
        // server-side -- so addNote reads it back. That read is what retired NoteHandler.
        assertTrue(result.isSuccess)
        assertEquals(NOTE_ID, noteDao.upserts.single().single().noteId)
    }

    @Test
    fun `addNote succeeds even when the read-back fails`() = runTest {
        callable.createPayload = mapOf("documentPath" to NOTE_ID)
        store.notes.clear()

        val result = repository.addNote(makeNote(isFavorite = false).copy(noteId = ""))

        // The note was created. Reporting a write that succeeded as an error because the follow-up
        // read failed would be worse than the note arriving on the next refresh.
        assertTrue(result.isSuccess)
        assertIs<AppError.NoteUnavailable>(errorHandler.loggedErrors.single())
    }

    @Test
    fun `addNote logs when the function returns no document path`() = runTest {
        val result = repository.addNote(makeNote(isFavorite = false).copy(noteId = ""))

        assertTrue(result.isSuccess)
        assertTrue(noteDao.upserts.isEmpty())
        assertIs<AppError.NoteUnavailable>(errorHandler.loggedErrors.single())
    }

    @Test
    fun `getNote mirrors the fetched note into Room`() = runTest {
        val result = repository.getNote(NOTE_ID)

        assertTrue(result.isSuccess)
        assertEquals(NOTE_ID, noteDao.upserts.single().single().noteId)
    }

    // -------------------------------------------------------------------------
    // The page cursor
    //
    // It used to wrap the last DocumentSnapshot, which the mocked suite never paged past. It wraps
    // the store's opaque position now, so these pin what a cursor is made from and when it applies.
    // -------------------------------------------------------------------------

    @Test
    fun `getNotes issues a cursor only for a full page`() = runTest {
        store.notes["note-2"] = noteFields()

        assertNotNull(repository.getNotes(pageSize = 2).getOrThrow().nextCursor)
        assertNull(repository.getNotes(pageSize = 3).getOrThrow().nextCursor)
    }

    @Test
    fun `getNotes resumes after the document its cursor names`() = runTest {
        store.notes["note-2"] = noteFields(text = "second")

        val first = repository.getNotes(pageSize = 1).getOrThrow()
        val second = repository.getNotes(cursor = first.nextCursor, pageSize = 1).getOrThrow()

        assertEquals(NOTE_ID, store.queries.last().startAfterId)
        assertEquals("note-2", second.notes.single().noteId)
    }

    @Test
    fun `getNotes ignores a cursor issued to another account`() = runTest {
        store.notes["note-2"] = noteFields()
        val cursor = repository.getNotes(pageSize = 1).getOrThrow().nextCursor
        authRepository.currentUserId = "user-2"

        repository.getNotes(cursor = cursor, pageSize = 1)

        // The previous account's position means nothing in this one's collection, so the query
        // starts from the top.
        assertEquals(
            NotesQuery("user-2", limit = 1, startAfterId = null, favoritesOnly = false),
            store.queries.last(),
        )
    }

    @Test
    fun `getNotes still pages on when an author lookup drops a note`() = runTest {
        store.notes["note-2"] = noteFields(userId = "author-2")
        store.failingAuthorIds += "author-2"

        val page = repository.getNotes(pageSize = 2).getOrThrow()

        // Two documents came back for a page of two. Reading the one surviving note as a short page
        // would end the feed here.
        assertEquals(listOf(NOTE_ID), page.notes.map { it.noteId })
        assertTrue(page.hasMore)
        assertEquals(1, errorHandler.loggedErrors.size)
    }

    @Test
    fun `syncFavoriteNotes replaces the mirrored favorites with the favorited documents`() = runTest {
        store.notes["fav-1"] = noteFields(favorite = true)
        noteDao.rows.value = listOf(
            makeNote(isFavorite = true).copy(noteId = "unfavorited-elsewhere").toDatabaseNote(),
            makeNote(isFavorite = false).copy(noteId = "paged-in").toDatabaseNote(),
        )

        repository.syncFavoriteNotes().getOrThrow()

        // Favorites only: the unfavorited note-1 must not be written in as though it were one.
        assertTrue(store.queries.single().favoritesOnly)
        assertEquals(listOf("paged-in", "fav-1"), noteDao.rows.value.map { it.noteId })
    }

    // -------------------------------------------------------------------------
    // What crosses the wire
    // -------------------------------------------------------------------------

    @Test
    fun `getNote reads media as the Vision function writes it`() = runTest {
        store.notes[NOTE_ID] = noteFields(
            extra = mapOf(
                "media_list" to listOf(
                    mapOf(
                        "videoUrl" to "https://video",
                        "thumbnailUrl" to "https://thumb",
                        "video" to true,
                        "generatedText" to listOf("receipt", null),
                        "generatedObjects" to listOf(mapOf("object" to "cup", "score" to 0.9)),
                        "generatedLabels" to listOf(mapOf("label" to "drink", "score" to 0.8)),
                        "generatedLandmarks" to listOf(mapOf("label" to "tower", "score" to 0.7)),
                        "generatedLogos" to listOf(mapOf("label" to "brand", "score" to 0.6)),
                    )
                )
            )
        )

        val media = repository.getNote(NOTE_ID).getOrThrow().mediaList?.single()

        assertEquals(
            MediaDetail(
                videoUrl = "https://video",
                thumbnailUrl = "https://thumb",
                isVideo = true,
                generatedTexts = listOf("receipt"),
                generatedObjects = listOf(DetectedObject("cup", 0.9)),
                generatedLabels = listOf(DetectedLabel("drink", 0.8)),
                generatedLandmarks = listOf(DetectedLabel("tower", 0.7)),
                generatedLogos = listOf(DetectedLabel("brand", 0.6)),
            ),
            media,
        )
    }

    @Test
    fun `a whole-number score is kept while a malformed detection is dropped`() = runTest {
        store.notes[NOTE_ID] = noteFields(
            extra = mapOf(
                "media_list" to listOf(
                    mapOf(
                        "photoUrl" to "https://photo",
                        "generatedLabels" to listOf(
                            mapOf("label" to "certain", "score" to 1L),
                            mapOf("label" to "unscored"),
                            mapOf("label" to "garbled", "score" to "high"),
                            mapOf("score" to 0.5),
                        ),
                    )
                )
            )
        )

        val note = repository.getNote(NOTE_ID).getOrThrow()

        // Firestore stores an integral JavaScript number as an integer, which reaches the client as
        // a Long. The Android SDK reader cast the score `as? Double` and so dropped "certain"; this
        // is the one reading that changed. The other three are dropped as before -- and alone, not
        // failing the note over one bad field.
        assertEquals(
            listOf(DetectedLabel("certain", 1.0)),
            note.mediaList?.single()?.generatedLabels,
        )
    }

    @Test
    fun `addNote sends createNote every media key even when null`() = runTest {
        repository.addNote(
            makeNote(isFavorite = false).copy(
                noteId = "",
                text = "trip",
                mediaList = listOf(
                    MediaDetail(photoUrl = "https://photo"),
                    MediaDetail(
                        videoUrl = "https://video",
                        thumbnailUrl = "https://thumb",
                        isVideo = true,
                    ),
                ),
            )
        )

        assertEquals(listOf("createNote"), callable.names)
        // The keys are the contract with functions/index.js, which reads each by name: a renamed
        // property would still compile, and send a key the function ignores.
        assertEquals(
            mapOf(
                "text" to "trip",
                "mediaList" to listOf(
                    mapOf(
                        "photoUrl" to "https://photo",
                        "videoUrl" to null,
                        "thumbnailUrl" to null,
                        "isVideo" to false,
                    ),
                    mapOf(
                        "photoUrl" to null,
                        "videoUrl" to "https://video",
                        "thumbnailUrl" to "https://thumb",
                        "isVideo" to true,
                    ),
                ),
            ),
            encode(CreateNoteArgs.serializer(), callable.createArgs.single()) {},
        )
    }

    @Test
    fun `updateNote sends updateNote the note id and its text`() = runTest {
        repository.updateNote(makeNote(isFavorite = false).copy(text = "edited"))

        assertEquals(listOf("updateNote"), callable.names)
        assertEquals(
            mapOf("noteId" to NOTE_ID, "text" to "edited"),
            encode(UpdateNoteArgs.serializer(), callable.updateArgs.single()) {},
        )
    }

    private fun noteFields(
        text: String = "hello",
        favorite: Boolean = false,
        userId: String? = AUTHOR_ID,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = buildMap {
        put("text", text)
        put("favorite", favorite)
        userId?.let { put("user_id", it) }
        putAll(extra)
    }

    private fun favoriteRows() = listOf(
        makeNote(isFavorite = true).copy(noteId = "a", text = "grocery list").toDatabaseNote(),
        makeNote(isFavorite = true).copy(noteId = "b", text = "errands").toDatabaseNote(),
    )

    private fun makeNote(isFavorite: Boolean) = Note(
        noteId = NOTE_ID,
        text = "hello",
        createdDate = null,
        isFavorite = isFavorite,
        mediaList = null,
        username = "alice",
        profilePictureUrl = null,
    )
}