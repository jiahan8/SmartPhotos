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
    // The three homeScreen_* goldens live here now, beside the screen they capture. They came from
    // :app's ScreenScreenshotTest, which captured Home and Search together because both screens
    // lived up there; splitting it sent one half here and the other to :feature:search. Four
    // modules capture screenshots now -- this one, :feature:search, :feature:settings and
    // :core:ui -- and each still restates the `roborazzi { outputDir }` block below, which is the
    // part a convention plugin could take over next.
    alias(libs.plugins.roborazzi)
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
        // and :core:ui's, which merge in through the dependency. HomeScreenTest wanted this first;
        // HomeScreenScreenshotTest renders the same screen the same way and wants it too.
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

roborazzi {
    // VCS-tracked rather than build/, so the PNGs are the committed baseline verifyRoborazziDebug
    // compares against. Same as :core:ui's and :feature:settings'.
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
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

    // The Roborazzi harness itself (BaseScreenshotTest, Robolectric, ui-test-junit4) arrives
    // through :core:testing's `api` block, which the feature convention already adds.
    testImplementation(libs.roborazzi.junit.rule)

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
