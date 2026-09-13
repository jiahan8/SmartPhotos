package com.jiahan.smartcamera.data.datastore

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [DefaultUserPreferencesRepository].
 *
 * Each case runs a real Preferences DataStore -- the real serializer and key handling -- over its
 * own in-memory [FakeFileSystem], so the cases are hermetic and touch no disk. Under Robolectric
 * this suite used a JUnit `TemporaryFolder`, which exists on no target but the JVM; okio's fake file
 * system exists on all of them, and DataStore's common storage is written against okio.
 *
 * The DataStore runs in `backgroundScope`, which `runTest` cancels as each case ends. That releases
 * the file path, which DataStore refuses to open twice in one process while a store over it is
 * active -- so the fixed path below is safe to reuse case to case.
 */
class DefaultUserPreferencesRepositoryTest {

    private fun TestScope.createRepository() = DefaultUserPreferencesRepository(
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FakeFileSystem(), PreferencesSerializer) {
                "/test.preferences_pb".toPath()
            },
            scope = backgroundScope,
        )
    )

    @Test
    fun updateDarkThemeVisibility_true_isPersistedAndEmitted() = runTest {
        val repository = createRepository()

        repository.setDarkTheme(true)

        assertTrue(repository.userPreferences.first().isDarkTheme)
    }

    @Test
    fun updateDarkThemeVisibility_false_isPersistedAndEmitted() = runTest {
        val repository = createRepository()

        repository.setDarkTheme(true)
        repository.setDarkTheme(false)

        assertFalse(repository.userPreferences.first().isDarkTheme)
    }

    @Test
    fun defaultPreferences_areReturnedWhenNothingPersisted() = runTest {
        val prefs = createRepository().userPreferences.first()

        assertFalse(prefs.isDarkTheme)
        assertEquals("", prefs.username)
        assertNull(prefs.profilePictureUrl)
    }

    @Test
    fun updateLocalUserProfile_persistsUsernameAndPicture() = runTest {
        val repository = createRepository()

        repository.updateLocalUserProfile("alice", "https://example.com/alice.png")

        val prefs = repository.userPreferences.first()
        assertEquals("alice", prefs.username)
        assertEquals("https://example.com/alice.png", prefs.profilePictureUrl)
    }

    @Test
    fun updateLocalUserProfile_nullPicture_removesStoredPicture() = runTest {
        val repository = createRepository()

        repository.updateLocalUserProfile("bob", "https://example.com/bob.png")
        repository.updateLocalUserProfile("bob", null)

        val prefs = repository.userPreferences.first()
        assertEquals("bob", prefs.username)
        assertNull(prefs.profilePictureUrl)
    }

    @Test
    fun clearUserScopedPreferences_dropsTheSignedInUsersIdentity() = runTest {
        val repository = createRepository()
        repository.updateLocalUserProfile("alice", "https://example.com/alice.png")

        repository.clearUserScopedPreferences()

        val prefs = repository.userPreferences.first()
        assertEquals("", prefs.username)
        assertNull(prefs.profilePictureUrl)
    }

    /**
     * The half of the split that is easy to get wrong: `preferences.clear()` would pass the
     * assertions above and silently reset the theme every time somebody signed out.
     */
    @Test
    fun clearUserScopedPreferences_keepsTheDeviceTheme() = runTest {
        val repository = createRepository()
        repository.setDarkTheme(true)
        repository.updateLocalUserProfile("alice", "https://example.com/alice.png")

        repository.clearUserScopedPreferences()

        assertTrue(repository.userPreferences.first().isDarkTheme)
    }
}