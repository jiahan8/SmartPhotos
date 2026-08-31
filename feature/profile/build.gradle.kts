/*
 * The fourth feature module: the Profile screen, its ViewModel, its route and both of its test
 * suites.
 *
 * It was the one the settings extraction named as blocked, and the block was real but small:
 * `ProfileViewModel` injects `MediaFileRepository` and calls `toMediaUri()`, and both lived in
 * :core:data, which a feature module must not depend on. Neither needed rewriting -- the interface
 * and the two extensions came *down* to :core:common, leaving `DefaultMediaFileRepository` in
 * :core:data, and because their Kotlin packages did not change, that move was a pure `git mv` with
 * no import churn anywhere in the build. **An Android-typed contract stranded beside its
 * implementation is a two-file move, not an architectural problem** -- worth knowing before
 * treating a `:core:data` injection as a reason a feature cannot be extracted.
 *
 * The interesting resource here is `profile` itself. It has two consumers -- this screen's title
 * and the bottom bar's tab label in `navigation/TopLevelDestination.kt` -- which looks like the
 * `explore` case, where one piece of copy turned out to be two strings and split. It is not: both
 * render the same word for the same destination, so the string travelled here and `:app` reads it
 * back as `ProfileR.string.profile`. That is a downward read like any other, `:app` already does
 * it for `UiR.string.search` in the same enum, and it is exactly what Now in Android's
 * `TopLevelDestination` does. Duplicating the string in both modules is the thing to avoid.
 */
plugins {
    id("smartphotos.android.feature")
    // ProfileRoute is @Serializable. Left out of the feature convention deliberately -- see the
    // note in :feature:settings.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jiahan.smartcamera.feature.profile"

    /*
     * No `sharedTest` source set here, unlike :feature:auth -- and that was tried rather than
     * assumed. ProfileScreenTest's KDoc says it is device-only because the bottom-anchored save
     * button and the inline validation text depend on real viewport and scroll behaviour, and
     * running it under Robolectric confirmed it exactly: three of its five tests passed and two
     * failed, `invalidUsername_showsValidationError` on the error text never being displayed and
     * `savingValidChange_invokesRepositoryUpdate` on a 5s timeout waiting for the save. So it
     * stays in androidTest, where it already was. **Prefer `sharedTest` for a new screen test, but
     * check the existing note before promoting an androidTest one into it** -- "androidTest-only"
     * is sometimes a finding, not an omission.
     */
}

dependencies {

    /*
     * :core:domain, :core:ui, Compose, icons, Hilt, lifecycle and :core:testing all arrive from
     * `smartphotos.android.feature`. What is left here is what only profile needs.
     */

    // MediaFileRepository and toMediaUri(), plus the email/name/username labels and the username
    // validators shared with :feature:auth. This is now the second feature to declare it -- the
    // point at which it would go into the feature convention if a third wants it for the same
    // reason. Auth wants the validators and profile wants the media seam, which are not the same
    // reason, so it stays here.
    implementation(project(":core:common"))

    // The photo-picker, camera and permission launchers ProfileScreen holds.
    implementation(libs.androidx.activity.compose)
    // ContextCompat.checkSelfPermission, for the camera permission check.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)

    // ProfileViewModelTest asserts on `events`, a SharedFlow with no `.value` to read.
    testImplementation(libs.turbine)

    // ProfileScreenTest builds its ViewModel from :core:testing's fakes and injects nothing, so the
    // default AndroidJUnitRunner is enough and this module declares no testInstrumentationRunner --
    // the same reasoning as :feature:auth and :feature:settings.
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
