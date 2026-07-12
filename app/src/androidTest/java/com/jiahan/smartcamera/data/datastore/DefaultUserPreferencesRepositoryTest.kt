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
 * Instrumented tests for [DefaultUserPreferencesRepository].
 *
 * Preferences DataStore requires a real file and coroutine machinery, so it is exercised as an
 * instrumented test. Each test runs against its own [DataStore] backed by a fresh file in a
 * per-test [TemporaryFolder], so the cases are fully hermetic and never touch the app's real
 * `user_preferences` store on the device.
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
        repository.updateDarkThemeVisibility(true)

        assertTrue(repository.userPreferencesFlow.first().isDarkTheme)
    }

    @Test
    fun updateDarkThemeVisibility_false_isPersistedAndEmitted() = runBlocking {
        repository.updateDarkThemeVisibility(true)
        repository.updateDarkThemeVisibility(false)

        assertFalse(repository.userPreferencesFlow.first().isDarkTheme)
    }

    @Test
    fun defaultPreferences_areReturnedWhenNothingPersisted() = runBlocking {
        val prefs = repository.userPreferencesFlow.first()

        assertFalse(prefs.isDarkTheme)
        assertEquals("", prefs.username)
        assertNull(prefs.profilePicture)
    }

    @Test
    fun updateLocalUserProfile_persistsUsernameAndPicture() = runBlocking {
        repository.updateLocalUserProfile("alice", "https://example.com/alice.png")

        val prefs = repository.userPreferencesFlow.first()
        assertEquals("alice", prefs.username)
        assertEquals("https://example.com/alice.png", prefs.profilePicture)
    }

    @Test
    fun updateLocalUserProfile_nullPicture_removesStoredPicture() = runBlocking {
        repository.updateLocalUserProfile("bob", "https://example.com/bob.png")
        repository.updateLocalUserProfile("bob", null)

        val prefs = repository.userPreferencesFlow.first()
        assertEquals("bob", prefs.username)
        assertNull(prefs.profilePicture)
    }
}