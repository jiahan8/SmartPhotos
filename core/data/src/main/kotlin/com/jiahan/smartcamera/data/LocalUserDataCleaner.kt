package com.jiahan.smartcamera.data

import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.database.dao.NoteDao
import javax.inject.Inject

/**
 * Erases everything on this device that belongs to the signed-in user.
 *
 * One list, in one place, so "what does signing out clear?" has a single answer. `AuthRepository`
 * used to inject [NoteDao] and call `clearAllNotes()` directly, which worked for exactly as long
 * as the notes mirror was the only per-user thing stored locally — and it had already stopped
 * being that: the DataStore kept the previous account's `username` and `profilePictureUrl`, and
 * nothing cleared them. The sign-in path happens to overwrite both, but only when `getUser()`
 * succeeds, so a sign-in with a failed profile fetch would render the *previous* user's name and
 * avatar. Adding a store meant remembering to edit a method on the auth repository; now it means
 * adding a line here.
 *
 * Deliberately not an interface with a `Default*` implementation and a `DataModule` binding: its
 * only consumer is [com.jiahan.smartcamera.data.repository.DefaultAuthRepository], one module away
 * from nothing, and Hilt resolves it straight from the constructor. Same shape as
 * `NoteShareDelegate` and `NoteErrorReporter`. Give it a seam when something outside :core:data
 * needs one.
 *
 * **What this is not:** it does not sign anybody out and does not touch the network — the caller
 * has already done that. It is the local half only.
 */
class LocalUserDataCleaner @Inject constructor(
    private val noteDao: NoteDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    /**
     * Clears the notes mirror, then the user-scoped preferences.
     *
     * Throws rather than returning a `Result`, because both call sites already run inside
     * `safeCall` and a clear that half-succeeded is a failure the caller has to see. The notes go
     * first: it is the larger store and the one holding the user's content, so if only one of the
     * two lands, that is the one worth landing.
     */
    suspend fun clearLocalUserData() {
        noteDao.clearAllNotes()
        userPreferencesRepository.clearUserScopedPreferences().getOrThrow()
    }
}