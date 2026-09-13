package com.jiahan.smartcamera.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.google.android.play.core.appupdate.AppUpdateManager
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AppUpdateRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.di.ApplicationScope
import com.jiahan.smartcamera.di.DebugBuild
import com.jiahan.smartcamera.di.IoDispatcher
import com.jiahan.smartcamera.database.AppDatabase
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
@RunWith(AndroidJUnit4::class)
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
    lateinit var photoRepository: PhotoRepository

    @Inject
    lateinit var appUpdateRepository: AppUpdateRepository

    @Inject
    lateinit var analyticsRepository: AnalyticsRepository

    @Inject
    lateinit var remoteConfigRepository: RemoteConfigRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var errorHandler: ErrorHandler

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var noteDao: NoteDao

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    /*
     * The three qualified bindings. A qualifier is the half of a binding a type alone cannot
     * express -- `CoroutineDispatcher` and `CoroutineScope` are ordinary types and `Boolean` is
     * one every graph could plausibly hold -- so these assert that the *annotation* still resolves,
     * not merely that something of that type exists.
     */
    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @DebugBuild
    @JvmField
    var isDebugBuild: Boolean = false

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
        assertNotNull(photoRepository)
        assertNotNull(appUpdateRepository)
        assertNotNull(analyticsRepository)
        assertNotNull(remoteConfigRepository)
        assertNotNull(userPreferencesRepository)
        assertNotNull(errorHandler)
        assertNotNull(appDatabase)
        assertNotNull(noteDao)
        assertNotNull(dataStore)
        assertNotNull(clock)
        assertNotNull(appUpdateManager)
        assertNotNull(ioDispatcher)
        assertNotNull(applicationScope)
    }
}