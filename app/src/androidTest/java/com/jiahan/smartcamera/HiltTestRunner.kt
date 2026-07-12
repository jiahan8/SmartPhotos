package com.jiahan.smartcamera

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom [AndroidJUnitRunner] that swaps in [HiltTestApplication] as the test [Application].
 *
 * Required for any `@HiltAndroidTest` to obtain a Hilt component. Tests that don't use Hilt (Room
 * DAO, DataStore, Compose UI) run unaffected — [HiltTestApplication] is just a plain Application.
 *
 * Referenced by `testInstrumentationRunner` in the app's build.gradle.kts.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}