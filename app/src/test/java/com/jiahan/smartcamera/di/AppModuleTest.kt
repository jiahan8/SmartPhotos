package com.jiahan.smartcamera.di

import com.jiahan.smartcamera.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the `@DebugBuild` binding to the real build type.
 *
 * The other half of this pair is `FirebaseRemoteConfigRepositoryTest` in :core:data, which drives
 * both fetch-interval branches by constructing the repository with the flag directly -- so it
 * never exercises the binding that supplies it. Without the assertion below, changing this
 * provider to a literal, or to the unused `BuildConfig.DEBUG_MODE` field, would silently flip
 * every injection site while both of those tests still passed.
 *
 * It stayed here when that file moved down because it names [BuildConfig] and [AppModule], and
 * neither exists below the application module -- which is the Build type rule in AGENTS.md read
 * from the test side: the reason a module below :app injects the flag is the same reason its test
 * cannot assert on the binding.
 */
class AppModuleTest {

    @Test
    fun `the DebugBuild binding reports the real build type`() {
        assertEquals(BuildConfig.DEBUG, AppModule.provideIsDebugBuild())
    }
}