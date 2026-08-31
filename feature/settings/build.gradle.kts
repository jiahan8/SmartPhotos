/*
 * The second feature module, and the one that made `smartphotos.android.feature` real.
 *
 * :feature:explore proved the structure with the smallest slice in the app. This one is chosen for
 * the opposite property: of the three feature packages that import nothing from `note/` -- auth,
 * profile, settings -- it is the only one with no upward reference outside `navigation/`. `auth` is
 * the start destination, so `MainViewModel`, `SmartPhotosApp` and `NavTransitions` all name it;
 * `profile` is a bottom-bar destination named by `TopLevelDestination`, and its ViewModel calls
 * `toMediaUri()`, which lives in :core:data and would have dragged that edge into a feature module.
 * The other four packages -- home, search, favorite, preview -- all share `note/`'s delegates and
 * cannot move until that is resolved.
 *
 * Three things had to move out from under it first, and each is a rule rather than a one-off:
 *
 * - `ValidationResult` went down to :core:domain. `validateNewPassword` came here with its
 *   `password_empty` string, because this module was its only caller; `validateUsername` and
 *   `validateDisplayName` stayed in :app for auth and profile. The shared *return type* had to
 *   land where all three can see it.
 * - `PasswordField` went down to :core:ui with `cd_hide_password`, `cd_show_password` and the two
 *   `visibility` drawables. This dialog held three copies of that block and AuthScreen a fourth --
 *   the `cd_back` case from the explore extraction, where a resource with consumers in two
 *   different future modules goes down rather than sideways.
 * - `BuildConfig.VERSION_NAME` became a `versionName` parameter on SettingsScreen, passed from the
 *   nav graph. A library has no application BuildConfig, and the alternative -- a `@VersionName`
 *   qualifier next to `@DebugBuild` -- would have bought nothing here: unlike `DEBUG`, a version
 *   string is not a branch condition, so there is no R8 constant-folding to preserve. Hoisting it
 *   also fixed a standing annoyance, since `settingsScreen_default.png` no longer goes stale on
 *   every version bump.
 */
plugins {
    id("smartphotos.android.feature")
    // SettingsRoute is @Serializable. Left out of the feature convention deliberately: a feature
    // with no destination of its own would not need it, and applying a compiler plugin no source
    // needs is hard to notice later.
    alias(libs.plugins.kotlin.serialization)
    // settingsScreen_default's golden lives here, beside the screen it captures -- the same move
    // NoteItemScreenshotTest made into :core:ui. This file is where the "a convention plugin could
    // take this over next" note was written; the fourth screenshot module is what collected on it.
    id("smartphotos.android.screenshot")
}

android {
    namespace = "com.jiahan.smartcamera.feature.settings"
}

dependencies {

    /*
     * Everything the other feature modules also want -- :core:domain (api), :core:ui, Compose,
     * icons, Hilt, lifecycle, :core:testing -- comes from `smartphotos.android.feature`. What is
     * left here is what only this module needs.
     */

    // ConfigurationCompat, to read the active locale for the Language row.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.turbine)

    /*
     * SettingsScreenTest came over with the screen. It builds its ViewModel from :core:testing's
     * fakes directly and injects nothing, so it needs no HiltTestRunner and no orchestrator -- the
     * default AndroidJUnitRunner is enough, which is why this module declares no
     * `testInstrumentationRunner` of its own. :app keeps its runner for the suites that do use
     * Hilt.
     */
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
