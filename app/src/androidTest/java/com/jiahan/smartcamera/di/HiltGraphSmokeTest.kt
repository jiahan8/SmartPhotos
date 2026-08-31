package com.jiahan.smartcamera.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.database.AppDatabase
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.dao.PhotoDao
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Validates that the real production Hilt graph assembles end-to-end.
 *
 * This is a DI smoke test: it field-injects the real (production) bindings from every
 * `SingletonComponent` module and asserts they resolve. A missing binding, a scoping mistake, or a
 * dependency cycle introduced anywhere in the graph will fail this test at [HiltAndroidRule.inject].
 *
 * It runs offline: none of the injected implementations perform network / Firebase I/O at
 * construction time (Firebase is auto-initialized on device but never called here), so no fakes are
 * substituted — the goal is precisely to exercise the *real* wiring.
 */
@HiltAndroidTest
class HiltGraphSmokeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var noteRepository: NoteRepository

    @Inject
    lateinit var mediaFileRepository: MediaFileRepository

    @Inject
    lateinit var analyticsRepository: AnalyticsRepository

    @Inject
    lateinit var remoteConfigRepository: RemoteConfigRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var errorHandler: ErrorHandler

    @Inject
    lateinit var resourceProvider: ResourceProvider

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var noteDao: NoteDao

    @Inject
    lateinit var photoDao: PhotoDao

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var clock: Clock

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun hiltGraph_providesEveryProductionSingleton() {
        assertNotNull(authRepository)
        assertNotNull(userRepository)
        assertNotNull(noteRepository)
        assertNotNull(mediaFileRepository)
        assertNotNull(analyticsRepository)
        assertNotNull(remoteConfigRepository)
        assertNotNull(userPreferencesRepository)
        assertNotNull(errorHandler)
        assertNotNull(resourceProvider)
        assertNotNull(appDatabase)
        assertNotNull(noteDao)
        assertNotNull(photoDao)
        assertNotNull(dataStore)
        assertNotNull(clock)
    }
}