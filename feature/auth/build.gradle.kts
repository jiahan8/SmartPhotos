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
    // AuthRoute is @Serializable. Left out of the feature convention deliberately -- see the note
    // in :feature:settings.
    alias(libs.plugins.kotlin.serialization)
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
     * :core:domain, :core:ui, Compose, icons, Hilt, lifecycle and :core:testing all arrive from
     * `smartphotos.android.feature`. What is left here is what only auth needs.
     */

    // validateUsername/validateDisplayName, and the field labels and username messages AuthScreen
    // and AuthViewModel resolve as `CommonR`. Not in the feature convention: profile is the only
    // other consumer, and it has not moved yet -- one module is a sample size of one.
    implementation(project(":core:common"))

    // AsyncImage, for the launcher icon the nav graph passes in as `logoRes`.
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.core)

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

    // AuthViewModelTest asserts on `navigationEvent`, a Channel-backed Flow with no `.value` to
    // read -- the case AGENTS.md names Turbine for. Not in the feature convention: explore's and
    // settings' suites do not need it.
    testImplementation(libs.turbine)

    // The JVM half of sharedTest. Robolectric is what makes AndroidJUnit4 resolve to a sandbox
    // rather than the on-device runner; no Roborazzi here, because this module captures no
    // screenshots.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    /*
     * The on-device half of the same file. It builds its ViewModel from :core:testing's fakes and
     * injects nothing, so the default AndroidJUnitRunner is enough and this module declares no
     * testInstrumentationRunner -- the same reasoning as :feature:settings.
     */
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
