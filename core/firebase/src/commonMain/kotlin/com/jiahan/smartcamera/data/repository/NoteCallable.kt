package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable

/**
 * The createNote and updateNote callables, by name, and how to read their rejections: the other seam
 * for [DefaultNoteRepository].
 *
 * [rejectionOf] is here for the reason [UserCallable] gives: the validation fold reads a rejection
 * no `commonTest` could otherwise construct.
 */
internal interface NoteCallable {
    suspend fun createNote(name: String, args: CreateNoteArgs): CreateNoteResult?

    suspend fun updateNote(name: String, args: UpdateNoteArgs)

    /** The code and `details.reason` [error] carries if it is a callable rejection, or null. */
    fun rejectionOf(error: Throwable): CallableRejection?
}

internal class GitLiveNoteCallable(
    private val functions: FirebaseFunctions,
) : NoteCallable {
    override suspend fun createNote(name: String, args: CreateNoteArgs): CreateNoteResult? =
        functions.httpsCallable(name)
            .invoke(CreateNoteArgs.serializer(), args)
            .data(CreateNoteResult.serializer().nullable)

    override suspend fun updateNote(name: String, args: UpdateNoteArgs) {
        functions.httpsCallable(name).invoke(UpdateNoteArgs.serializer(), args)
    }

    override fun rejectionOf(error: Throwable): CallableRejection? = error.toCallableRejection()
}

/*
 * The argument classes declare no defaults, so every key is sent even when its value is null -- the
 * shape the Android SDK's hashMapOf sent. Their property names are the wire contract with
 * functions/index.js, which reads each key by name.
 */

@Serializable
internal class CreateNoteArgs(
    val text: String?,
    val mediaList: List<CreateNoteMedia>,
)

@Serializable
internal class CreateNoteMedia(
    val photoUrl: String?,
    val videoUrl: String?,
    val thumbnailUrl: String?,
    val isVideo: Boolean,
)

@Serializable
internal class UpdateNoteArgs(
    val noteId: String,
    val text: String?,
)

/**
 * createNote's reply. `documentPath` holds the new note's id, despite its name. A reply that is not a
 * map, or carries no such key, reads as null here -- the note was still created, so the repository
 * logs rather than fails.
 */
@Serializable
internal class CreateNoteResult(
    val documentPath: String? = null,
)