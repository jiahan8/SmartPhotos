/*
 * The first feature module. Everything phases 6, 7 and 9 moved below :app was there to make this
 * possible; this is the first thing to actually use it.
 *
 * It is deliberately the smallest slice in the app. `explore` is the only feature package that
 * imports nothing from `note/` -- no NoteHandler, no NoteActionsDelegate, no NoteShareDelegate --
 * and its route carries no arguments, so no ViewModel reads it back. What it proves is the
 * structure, not the payoff: that a feature compiles against :core:ui and :core:domain alone, that
 * its @HiltViewModel still resolves into the component :app generates, and that its resources and
 * tests travel with it.
 *
 * No `smartphotos.android.feature` convention plugin yet, on build-logic's own rule: put a setting
 * there when more than one module wants it for the same reason. One feature module is a sample size
 * of one, and the shape of the second is exactly what this module exists to discover.
 */
plugins {
    id("smartphotos.android.library")
    id("smartphotos.android.compose")
    // ExploreRoute is @Serializable. Navigation Compose generates the route pattern from it.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jiahan.smartcamera.feature.explore"
}

dependencies {

    /*
     * api, not implementation: Photo appears on ExploreUiState, which is public, so anything
     * reading that state compiles against :core:domain through this edge. It is also what Hilt
     * needs -- PhotoRepository, AnalyticsRepository and ErrorHandler are @Inject constructor
     * parameters of ExploreViewModel, and :app's annotation processor has to resolve them itself
     * when it generates the single SingletonComponent.
     */
    api(project(":core:domain"))

    /*
     * implementation, deliberately -- and this is the counterpart to :core:ui's api block rather
     * than a contradiction of it. That module exports Compose because Modifier is a *parameter* of
     * 25 public composables there. Nothing here hands out a Compose type: ExploreScreen takes two
     * lambdas and its own ViewModel. So this module consumes Compose without re-exporting it, which
     * is the shape Now in Android's feature modules use.
     */
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.core)
    // Rounded.Search and AutoMirrored.Filled.ArrowBack are not in material-icons-core.
    implementation(libs.androidx.material.icons.extended)
    // BackHandler, in ExploreScreen's search mode.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)

    debugImplementation(libs.androidx.ui.tooling)

    // MainDispatcherRule lived here in duplicate until :core:testing landed.
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}