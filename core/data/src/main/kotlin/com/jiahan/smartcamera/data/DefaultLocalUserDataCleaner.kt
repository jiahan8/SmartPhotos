package com.jiahan.smartcamera.data

import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.database.dao.NoteDao
import javax.inject.Inject

/**
 * [LocalUserDataCleaner] over this device's per-user stores.
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
 * It was a plain class, with no interface, while its only consumer sat in this module, and became
 * a seam exactly when something outside :core:data needed one: `DefaultAuthRepository` moving to
 * :core:firebase. The list stayed here, beside the stores it names.
 */
class DefaultLocalUserDataCleaner @Inject constructor(
    private val noteDao: NoteDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) : LocalUserDataCleaner {

    /**
     * Clears the notes mirror, then the user-scoped preferences.
     *
     * The notes go first: it is the larger store and the one holding the user's content, so if only
     * one of the two lands, that is the one worth landing.
     */
    override suspend fun clearLocalUserData() {
        noteDao.clearAllNotes()
        userPreferencesRepository.clearUserScopedPreferences().getOrThrow()
    }
}