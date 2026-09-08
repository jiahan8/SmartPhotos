package com.jiahan.smartcamera.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Tests for [DefaultUserPreferencesRepository].
 *
 * Preferences DataStore requires a real file and coroutine machinery, so this never ran as a plain
 * JVM test -- but Robolectric supplies both, so it lives in `sharedTest` and runs on the JVM in CI
 * as well as on-device. Each test runs against its own [DataStore] backed by a fresh file in a
 * per-test [TemporaryFolder], so the cases are fully hermetic and never touch the app's real
 * `user_preferences` store on the device.
 *
 * No `@Config` here, unlike this module's `src/test` Robolectric suites: the annotation is
 * Robolectric's and would not resolve on the androidTest half of a `sharedTest` source set. It is
 * not needed either -- a library module declares no custom `Application`, so Robolectric already
 * instantiates a plain one.
 */
@RunWith(AndroidJUnit4::class)
class DefaultUserPreferencesRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DefaultUserPreferencesRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test.preferences_pb") }
        )
        repository = DefaultUserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun updateDarkThemeVisibility_true_isPersistedAndEmitted() = runBlocking {
        repository.setDarkTheme(true)

        assertTrue(repository.userPreferences.first().isDarkTheme)
    }

    @Test
    fun updateDarkThemeVisibility_false_isPersistedAndEmitted() = runBlocking {
        repository.setDarkTheme(true)
        repository.setDarkTheme(false)

        assertFalse(repository.userPreferences.first().isDarkTheme)
    }

    @Test
    fun defaultPreferences_areReturnedWhenNothingPersisted() = runBlocking {
        val prefs = repository.userPreferences.first()

        assertFalse(prefs.isDarkTheme)
        assertEquals("", prefs.username)
        assertNull(prefs.profilePictureUrl)
    }

    @Test
    fun updateLocalUserProfile_persistsUsernameAndPicture() = runBlocking {
        repository.updateLocalUserProfile("alice", "https://example.com/alice.png")

        val prefs = repository.userPreferences.first()
        assertEquals("alice", prefs.username)
        assertEquals("https://example.com/alice.png", prefs.profilePictureUrl)
    }

    @Test
    fun updateLocalUserProfile_nullPicture_removesStoredPicture() = runBlocking {
        repository.updateLocalUserProfile("bob", "https://example.com/bob.png")
        repository.updateLocalUserProfile("bob", null)

        val prefs = repository.userPreferences.first()
        assertEquals("bob", prefs.username)
        assertNull(prefs.profilePictureUrl)
    }

    @Test
    fun clearUserScopedPreferences_dropsTheSignedInUsersIdentity() = runBlocking {
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
    fun clearUserScopedPreferences_keepsTheDeviceTheme() = runBlocking {
        repository.setDarkTheme(true)
        repository.updateLocalUserProfile("alice", "https://example.com/alice.png")

        repository.clearUserScopedPreferences()

        assertTrue(repository.userPreferences.first().isDarkTheme)
    }
}