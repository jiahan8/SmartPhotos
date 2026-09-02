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
 * The `smartphotos.android.feature` convention plugin arrived with the second feature module,
 * :feature:settings, which wanted this file's :core:domain/:core:ui edges, its Compose set, Hilt,
 * lifecycle and :core:testing verbatim. What stayed here is what settings does not want: coil,
 * activity-compose and serialization -- the icon packs turned out to be shared and went into the
 * plugin.
 */
plugins {
    id("smartphotos.android.feature")
}

android {
    namespace = "com.jiahan.smartcamera.feature.explore"
}

dependencies {

    /*
     * :core:domain, :core:ui, the Compose set, icons, Hilt, lifecycle, the serialization plugin and
     * its runtime, and the whole test/androidTest baseline -- :core:testing, junit, mockk,
     * kotlinx-coroutines-test, Turbine and the five on-device lines -- all arrive from
     * `smartphotos.android.feature`. What is left here is what only this feature needs.
     */

    // BackHandler, in ExploreScreen's search mode.
    implementation(libs.androidx.activity.compose)
    // AsyncImage, for the Unsplash photos.
    implementation(libs.coil.compose)
}