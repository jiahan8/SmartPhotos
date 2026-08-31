/*
 * The seventh feature module: the Home feed, its ViewModel, its route and its `sharedTest` Compose
 * suite.
 *
 * Two strings travelled and one could not. `create_first_note` and `cd_open_explore` are this
 * screen's alone -- the second of those exists *because* of the explore extraction, which split one
 * piece of copy into a screen title that travelled and a contentDescription that stayed. `app_name`
 * is the third case again and the one with no split available: it is the application's manifest
 * label, so a library cannot own it. It became a `title` parameter, the same hoist as
 * :feature:settings' `versionName` and :feature:auth's `logoRes`. **Reach for a qualifier when the
 * value picks a code path; reach for a parameter when it is rendered.**
 *
 * `HomeScreenTest` came over from :app's `sharedTest`, and the arrangement came with it -- see the
 * note on the source sets below.
 */
plugins {
    id("smartphotos.android.feature")
    // HomeRoute is @Serializable. Left out of the feature convention deliberately -- see the note
    // in :feature:settings.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jiahan.smartcamera.feature.home"

    sourceSets {
        // The arrangement :feature:auth's note says to copy for the next feature, copied. One
        // Compose behaviour suite compiled into both source sets: on the JVM under Robolectric for
        // CI, and on-device under the instrumentation runner.
        getByName("test").java.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").java.srcDir("src/sharedTest/kotlin")
    }

    testOptions {
        // Robolectric renders HomeScreen on the JVM and needs this module's own strings with it,
        // and :core:ui's, which merge in through the dependency.
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {

    /*
     * :core:domain, :core:ui, Compose, icons, Hilt, lifecycle, :core:testing, junit, mockk and
     * kotlinx-coroutines-test all arrive from `smartphotos.android.feature`.
     */

    // NoteShareDelegate and NoteErrorReporter.
    implementation(project(":core:common"))

    // ShareCompat.IntentBuilder, for the share chooser.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)

    // HomeViewModelTest asserts on `actionError`, a SharedFlow with no `.value` to read.
    testImplementation(libs.turbine)

    // createAndroidComposeRule() launches a ComponentActivity that exists only in the manifest this
    // artifact merges into the debug variant -- see the same note in :core:ui.
    debugImplementation(libs.androidx.ui.test.manifest)

    // sharedTest/ runs in both source sets, so :core:testing is needed in both -- the same
    // reasoning as :feature:auth and :app.
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
