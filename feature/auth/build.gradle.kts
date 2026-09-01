/*
 * The third feature module: the Auth screen, its ViewModel and its route.
 *
 * `:feature:settings` was picked for having no upward reference outside `navigation/`. Auth does
 * have some -- `MainViewModel`, `SmartPhotosApp` and `NavTransitions` all name `AuthRoute`, because
 * it is the start destination -- and that turned out not to matter at all: every one of those is in
 * `:app`, so they point *down* into this module, which is the direction the dependency already
 * runs. What actually decides whether a feature can move is what it reaches *up* for, and auth's
 * list was short: every repository it injects is already an interface in :core:domain, and every
 * composable it draws with is already in :core:ui.
 *
 * Three things had to move first, and none of them was a Compose problem:
 *
 * - `validateUsername`/`validateDisplayName` went down to the new :core:common, with the ten
 *   username/name/email strings auth and profile share. That module exists because neither
 *   :core:domain (no Android plugin, so no resources) nor :core:ui (Compose vocabulary, and a
 *   validator is not a composable) could take them.
 * - `usernameErrorMessageResId` was deleted rather than moved. It read an ALREADY_EXISTS /
 *   INVALID_ARGUMENT code off a `FirebaseFunctionsException` in the ViewModel layer, so relocating
 *   it would have put firebase-functions on this module's classpath. DefaultUserRepository now
 *   raises `AppError.UsernameTaken`/`UsernameReserved` and `getErrorMessage` renders them -- the
 *   rule the rest of the data layer already followed, applied to the one place that had not.
 * - `AuthScreen` took a `logoRes` parameter. It drew `R.mipmap.ic_launcher`, which belongs to the
 *   application module; the launcher icon is :app's by definition and copying it here would fork
 *   an asset. Same hoist as SettingsScreen's `versionName`, for the same reason.
 *
 * What did NOT have to move is worth recording too: `password`, `login`, the verification-email
 * copy and `cd_app_logo` are auth's alone, so all seventeen came here. Only the strings profile
 * also reads went down.
 */
plugins {
    id("smartphotos.android.feature")
}

android {
    namespace = "com.jiahan.smartcamera.feature.auth"

    sourceSets {
        // AuthScreenTest came over from :app's sharedTest, and so did the arrangement: one Compose
        // behaviour suite compiled into both source sets, running on the JVM under Robolectric for
        // CI and on-device under the instrumentation runner. :feature:settings' screen test is
        // androidTest-only, which is the weaker arrangement -- copy this one for the next feature.
        getByName("test").java.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").java.srcDir("src/sharedTest/kotlin")
    }

    testOptions {
        // Robolectric renders AuthScreen on the JVM and needs this module's own strings with it --
        // and :core:common's, which merge in through the dependency.
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {

    /*
     * :core:domain, :core:ui, the Compose set, icons, Hilt, lifecycle, the serialization plugin and
     * its runtime, and the whole test/androidTest baseline -- :core:testing, junit, mockk,
     * kotlinx-coroutines-test, Turbine and the five on-device lines -- all arrive from
     * `smartphotos.android.feature`. What is left here is what only this feature needs.
     */

    // validateUsername/validateDisplayName, and the field labels and username messages AuthScreen
    // and AuthViewModel resolve as `CommonR`. Seven of the nine features declare this edge now, but
    // deliberately not from the convention: they want different tenants of the module -- auth the
    // validators, profile the media seam, the four note screens the delegates -- and the rule here
    // is "more than one module wants it, for the same reason". Explore and settings want none of
    // it, and a convention that gave it to them would hide that.
    implementation(project(":core:common"))

    // AsyncImage, for the launcher icon the nav graph passes in as `logoRes`.
    implementation(libs.coil.compose)

    /*
     * `@Preview` on AuthScreen's previews. This is `implementation`, not the `debugImplementation`
     * it looks like it could be, and the difference is a broken release build: the feature
     * convention adds `debugImplementation(ui-tooling)`, which drags ui-tooling-preview onto the
     * debug classpath only -- so the annotation resolves in debug and `compileReleaseKotlin` fails
     * with "Unresolved reference 'Preview'". Nothing caught it, because assembleDebug, the unit
     * tests, Roborazzi and lintDebug all compile the debug variant and CI builds no other.
     *
     * :core:ui declares it the same way for NoteItem's previews. Two modules, but not a convention
     * yet: :core:ui applies the library convention rather than the feature one, so :feature:auth is
     * still the only *feature* that draws a preview -- a sample size of one, by the rule in
     * build-logic. Move it into the feature plugin when a second feature adds a `@Preview`.
     */
    implementation(libs.androidx.ui.tooling.preview)

    // The JVM half of sharedTest. Robolectric is what makes AndroidJUnit4 resolve to a sandbox
    // rather than the on-device runner; no Roborazzi here, because this module captures no
    // screenshots.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
}
