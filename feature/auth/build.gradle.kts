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
 *   validator is not a composable) could take them. The validators went one module further later,
 *   to :core:domain, when `ValidationResult.Error` traded its `R.string` id for an identity; the
 *   strings and the mapper that renders them stayed.
 * - `usernameErrorMessageResId` was deleted rather than moved. It read an ALREADY_EXISTS /
 *   INVALID_ARGUMENT code off a `FirebaseFunctionsException` in the ViewModel layer, so relocating
 *   it would have put firebase-functions on this module's classpath. DefaultUserRepository now
 *   raises `AppError.UsernameTaken`/`UsernameReserved` and `appErrorMessageResId` renders them --
 *   the rule the rest of the data layer already followed, applied to the one place that had not.
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

}

dependencies {

    /*
     * :core:domain, :core:ui, the Compose set, icons, Hilt, lifecycle, the serialization plugin and
     * its runtime, and the whole test/androidTest baseline -- :core:testing, junit, mockk,
     * kotlinx-coroutines-test, Turbine and the five on-device lines -- all arrive from
     * `smartphotos.android.feature`. What is left here is what only this feature needs.
     */

    // `validationErrorMessageResId`, `ErrorMessage.resolve`, and the field labels and username
    // messages AuthScreen resolves as `CommonR`. Every feature declares this edge now. It stayed
    // out of the convention because features wanted different tenants of the module -- auth and
    // settings the validation strings, profile the media seam, the four note screens the delegates
    // -- and Explore wanted none, failing the rule "more than one module wants it, for the same
    // reason". Screens resolving their own failure text gave all nine the same reason,
    // `ErrorMessage.resolve`, so that argument no longer holds. Moving the edge into
    // `smartphotos.android.feature` is a follow-up, deliberately not bundled with that change.
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
}