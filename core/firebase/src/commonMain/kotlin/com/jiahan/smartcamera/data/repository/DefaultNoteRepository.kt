package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.data.toDatabaseNote
import com.jiahan.smartcamera.database.data.toNote
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NotePage
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.safeCall
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [NoteRepository] on GitLive's multiplatform Firestore and Functions, writing everything it fetches
 * into Room through [NoteDao].
 *
 * Firebase sits behind [NoteStore] and [NoteCallable], so the suite runs in `commonTest`.
 */
class DefaultNoteRepository internal constructor(
    private val authRepository: AuthRepository,
    private val store: NoteStore,
    private val callable: NoteCallable,
    private val noteDao: NoteDao,
    private val errorHandler: ErrorHandler,
) : NoteRepository {

    constructor(
        authRepository: AuthRepository,
        firestore: FirebaseFirestore,
        functions: FirebaseFunctions,
        noteDao: NoteDao,
        errorHandler: ErrorHandler,
    ) : this(
        authRepository,
        GitLiveNoteStore(firestore),
        GitLiveNoteCallable(functions),
        noteDao,
        errorHandler,
    )

    private companion object {
        const val REASON_TEXT_TOO_LONG = "TEXT_TOO_LONG"
        const val REASON_TOO_MANY_MEDIA = "TOO_MANY_MEDIA_ITEMS"
        const val REASON_EMPTY_NOTE = "EMPTY_NOTE"
        const val FUNCTION_CREATE_NOTE = "createNote"
        const val FUNCTION_UPDATE_NOTE = "updateNote"
    }

    /**
     * The Firestore document a page ended on. Carries the account it was issued for so a cursor
     * left over from a previous sign-in is ignored rather than applied to another user's notes.
     */
    private data class FirestoreNoteCursor(
        val userId: String,
        val position: NotePosition
    ) : NoteCursor

    override suspend fun getNotes(cursor: NoteCursor?, pageSize: Int): Result<NotePage> = safeCall {
        val currentUserId = authRepository.currentUserId ?: return@safeCall NotePage(emptyList())

        val startAfter = (cursor as? FirestoreNoteCursor)
            ?.takeIf { it.userId == currentUserId }
            ?.position
        val documents = store.getNotes(currentUserId, limit = pageSize, startAfter = startAfter)
        val notes = buildNotes(documents)
        // The cursor comes from the documents the query returned, not from the mapped
        // notes: fetchAuthors tolerates a failed author lookup by dropping that
        // note, so a short mapped list would otherwise be read as "end of feed".
        val lastDocument = documents
            .takeIf { it.size >= pageSize }
            ?.lastOrNull()
        cacheNotes(notes)
        NotePage(
            notes = notes,
            nextCursor = lastDocument?.let { FirestoreNoteCursor(currentUserId, it.position) }
        )
    }

    // Delegates to the createNote Cloud Function, which enforces
    // MAX_NOTE_TEXT_LENGTH and stamps the true owner into user_id server-side
    // instead of trusting a client-supplied value.
    override suspend fun addNote(note: Note): Result<Unit> = safeCall {
        val args = CreateNoteArgs(
            text = note.text,
            mediaList = note.mediaList.orEmpty().map { media ->
                CreateNoteMedia(
                    photoUrl = media.photoUrl,
                    videoUrl = media.videoUrl,
                    thumbnailUrl = media.thumbnailUrl,
                    isVideo = media.isVideo
                )
            },
        )
        mirrorCreatedNote(callable.createNote(FUNCTION_CREATE_NOTE, args))
    }.foldNoteValidationError()

    // Delegates to the updateNote Cloud Function for the same reason addNote
    // delegates to createNote: server-side validation and ownership checks a
    // direct client-side Firestore update couldn't do. Only [note]'s text
    // is sent -- a note's media is fixed at creation time -- but the whole note
    // is taken so the local mirror can be refreshed with it below.
    override suspend fun updateNote(note: Note): Result<Unit> = safeCall {
        callable.updateNote(FUNCTION_UPDATE_NOTE, UpdateNoteArgs(noteId = note.noteId, text = note.text))
        // Unconditional: this write used to be gated on `note.isFavorite`, back when the table
        // held favorites only. It mirrors the whole feed now, so an edit to any note has to land.
        noteDao.upsertNotes(listOf(note.toDatabaseNote()))
    }.foldNoteValidationError()

    // Reads the whole collection and filters client-side, which is what it has always done. What
    // changed is where the result goes: it is mirrored on the way out, so searchNotesStream covers
    // notes the feed never paged rather than narrowing search to what Home has scrolled.
    override suspend fun searchNotes(query: String): Result<List<Note>> = safeCall {
        val userId = authRepository.currentUserId ?: return@safeCall emptyList()
        val matches = store.getNotes(userId).filter { document ->
            matchesQuery(
                document.fields.text,
                document.fields.mediaList?.map { it.toMediaDetail() },
                query
            )
        }
        buildNotes(matches).also { cacheNotes(it) }
    }

    override suspend fun deleteNote(noteId: String): Result<Unit> = safeCall {
        authRepository.currentUserId?.let { store.deleteNote(it, noteId) }
        noteDao.deleteNote(noteId)
    }

    override suspend fun toggleFavorite(note: Note): Result<Unit> = safeCall {
        val newFavoriteStatus = note.isFavorite.not()
        authRepository.currentUserId?.let { store.setFavorite(it, note.noteId, newFavoriteStatus) }
        // One upsert for both directions. Unfavoriting used to delete the row, which was right
        // when the table was a favorites-only cache and wrong now that it mirrors the feed -- the
        // note still exists, it is just no longer favorited. Upsert rather than the DAO's
        // `updateFavorite` because the row may not be there yet: a note can be favorited straight
        // from a screen that never paged it in, and an UPDATE against a missing row is a silent
        // no-op that would lose it from Favorite.
        noteDao.upsertNotes(
            listOf(note.copy(isFavorite = newFavoriteStatus).toDatabaseNote())
        )
    }

    override suspend fun getNote(noteId: String): Result<Note> = safeCall {
        val userId = authRepository.currentUserId ?: throw AppError.NotAuthenticated()
        val document = store.getNote(userId, noteId) ?: throw AppError.NoteUnavailable()
        val authorId = document.fields.userId ?: throw AppError.NoteUnavailable()
        val author = store.getAuthor(authorId) ?: throw AppError.NoteUnavailable()
        buildNote(document, author).also { cacheNotes(listOf(it)) }
    }

    /**
     * Reads a just-created note back so it lands in the mirror.
     *
     * The client cannot build that row itself -- the id and the `created` timestamp are both
     * stamped server-side -- and until this existed a new note reached the feed only through a
     * `NoteHandler` event telling Home to refetch everything. One extra document read retires that
     * whole mechanism.
     *
     * Deliberately not fatal: the note *was* created, so failing here would report a write that
     * succeeded as an error. A lost read-back costs the user a pull-to-refresh, which is exactly
     * what a failed reload cost them before.
     */
    private suspend fun mirrorCreatedNote(result: CreateNoteResult?) {
        val noteId = result?.documentPath
        if (noteId == null) {
            errorHandler.logError(AppError.NoteUnavailable())
            return
        }
        getNote(noteId).onFailure(errorHandler::logError)
    }

    /**
     * Folds a createNote/updateNote validation rejection into an [AppError].
     *
     * Both functions signal every validation failure as one `invalid-argument` code and tell them
     * apart in a structured `details.reason` payload, so this reads the payload rather than the
     * code -- the one way it differs from how [AppError.UsernameTaken] is folded. Doing it here
     * rather than in a ViewModel-layer mapper is what keeps `FirebaseFunctionsException` below the
     * repository boundary, and off a feature module's classpath. The rejection is read through
     * [NoteCallable.rejectionOf], which is what lets the suite hand it one.
     *
     * An unrecognised reason is left alone: it means a malformed request no legitimate client can
     * produce, and it should surface as the generic failure rather than as a specific message that
     * happens to be wrong.
     */
    private fun <T> Result<T>.foldNoteValidationError(): Result<T> {
        val reason = exceptionOrNull()?.let(callable::rejectionOf)?.reason
        return when (reason) {
            REASON_TEXT_TOO_LONG -> Result.failure(AppError.NoteTextTooLong())
            REASON_TOO_MANY_MEDIA -> Result.failure(AppError.NoteMediaLimitExceeded())
            REASON_EMPTY_NOTE -> Result.failure(AppError.NoteEmpty())
            else -> this
        }
    }

    /**
     * Mirrors a fetched page into Room.
     *
     * No longer best effort. It was, while nothing read this table for the feed and a failed cache
     * write blanking a screen whose fetch succeeded would have been the worse trade. Home renders
     * the mirror now, so a page that fails to land is a page the user cannot see -- and swallowing
     * that would also let the caller advance its cursor past it, turning a lost write into a
     * permanent hole in the feed. Failing the enclosing [safeCall] leaves the cursor where it was,
     * so the page is retried instead.
     */
    private suspend fun cacheNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        noteDao.upsertNotes(notes.map { it.toDatabaseNote() })
    }

    // Still favorites-only, and still correct: the DAO's syncFavoriteNotes clears the favorited
    // rows and reinserts what the server says is favorited, leaving the mirrored non-favorites
    // alone. It becomes a full sync when the feed moves onto Room.
    override suspend fun syncFavoriteNotes(): Result<Unit> = safeCall {
        val favorites = authRepository.currentUserId
            ?.let { buildNotes(store.getNotes(it, favoritesOnly = true)) }
            .orEmpty()
        noteDao.syncFavoriteNotes(favorites.map { it.toDatabaseNote() })
    }

    /**
     * Pairs each document with its author, dropping a document with no author id or whose author
     * lookup failed.
     */
    private suspend fun buildNotes(documents: List<NoteDocument>): List<Note> {
        val authors = fetchAuthors(documents.mapNotNull { it.fields.userId }.distinct())
        return documents.mapNotNull { document ->
            val userId = document.fields.userId ?: return@mapNotNull null
            authors[userId]?.let { buildNote(document, it) }
        }
    }

    /**
     * Fetches author documents in parallel.
     * A single failed lookup is logged and skipped (partial-result tolerance). An author document
     * that does not exist is not a failure here -- its notes render with an empty name -- which is
     * the one way this differs from [getNote].
     */
    private suspend fun fetchAuthors(userIds: List<String>): Map<String, FirestoreAuthor> {
        if (userIds.isEmpty()) return emptyMap()
        return coroutineScope {
            userIds.map { userId ->
                async {
                    safeCall { userId to (store.getAuthor(userId) ?: FirestoreAuthor()) }
                        .onFailure(errorHandler::logError)
                        .getOrNull()
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    override fun getNotesStream(limit: Int): Flow<List<Note>> =
        noteDao.getNotes(limit).map { notes -> notes.map { it.toNote() } }

    override fun getNoteStream(noteId: String): Flow<Note?> =
        noteDao.getNote(noteId).map { note -> note?.toNote() }

    override fun searchNotesStream(query: String): Flow<List<Note>> =
        noteDao.getNotes().map { notes ->
            notes.filter { note -> matchesQuery(note.text, note.mediaList, query) }
                .map { it.toNote() }
        }

    override fun getFavoriteNotesStream(query: String): Flow<List<Note>> =
        noteDao.getFavoriteNotes().map { notes ->
            val matches =
                if (query.isBlank()) notes
                else notes.filter { note -> matchesQuery(note.text, note.mediaList, query) }
            matches.map { it.toNote() }
        }

    private fun buildNote(document: NoteDocument, author: FirestoreAuthor) = Note(
        noteId = document.id,
        text = document.fields.text,
        createdDate = document.fields.created?.toInstant(),
        isFavorite = document.fields.favorite == true,
        mediaList = document.fields.mediaList?.map { it.toMediaDetail() },
        username = author.username ?: "",
        profilePictureUrl = author.profilePicture
    )

    /** Shared text/media match predicate used by both Firestore search and local favorite filtering. */
    private fun matchesQuery(text: String?, mediaList: List<MediaDetail>?, query: String): Boolean =
        text?.contains(query, ignoreCase = true) == true ||
                mediaList?.any { media ->
                    media.generatedTexts?.any { it.contains(query, ignoreCase = true) } == true ||
                            media.generatedObjects?.any {
                                it.objectName.contains(query, ignoreCase = true)
                            } == true ||
                            media.generatedLabels?.any {
                                it.label.contains(query, ignoreCase = true)
                            } == true ||
                            media.generatedLandmarks?.any {
                                it.label.contains(query, ignoreCase = true)
                            } == true ||
                            media.generatedLogos?.any {
                                it.label.contains(query, ignoreCase = true)
                            } == true
                } == true
}