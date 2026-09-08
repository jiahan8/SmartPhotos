package com.jiahan.smartcamera.data

import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.database.dao.NoteDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the list of what "local user data" means, which is the only reason this class exists.
 *
 * A store that is added and not cleared reads exactly like one that is — there is no compile error
 * and no failing assertion anywhere else — so the coverage has to be here, naming each store.
 */
class LocalUserDataCleanerTest {

    private val noteDao: NoteDao = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    private val cleaner = LocalUserDataCleaner(noteDao, userPreferencesRepository)

    @Test
    fun `clearLocalUserData clears the notes mirror`() = runTest {
        coEvery { userPreferencesRepository.clearUserScopedPreferences() } returns
                Result.success(Unit)

        cleaner.clearLocalUserData()

        coVerify { noteDao.clearAllNotes() }
    }

    @Test
    fun `clearLocalUserData clears the user-scoped preferences`() = runTest {
        // The store the old NoteDao-only clear forgot: without this the next sign-in could render
        // the previous account's username and avatar.
        coEvery { userPreferencesRepository.clearUserScopedPreferences() } returns
                Result.success(Unit)

        cleaner.clearLocalUserData()

        coVerify { userPreferencesRepository.clearUserScopedPreferences() }
    }

    @Test
    fun `clearLocalUserData fails when a store cannot be cleared`() = runTest {
        // Surfaces rather than swallows: both callers wrap this in safeCall, and a half-cleared
        // device is something sign-out has to report rather than claim as success.
        coEvery { userPreferencesRepository.clearUserScopedPreferences() } returns
                Result.failure(IllegalStateException("datastore unavailable"))

        val thrown = runCatching { cleaner.clearLocalUserData() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }
}