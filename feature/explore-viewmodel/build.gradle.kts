/*
 * Kotlin Multiplatform module: ExploreViewModel, the first ViewModel in `commonMain`.
 *
 * The other half of :feature:explore, split off so the ViewModel compiles for the JVM and the Apple
 * targets while the screen, the route, the tests and the Hilt wiring stay in the Android module.
 * Explore went first because it was nearly there already: every constructor parameter is a
 * :core:domain interface, it holds no `Uri` and reads no route argument, and since screens resolve
 * their own text its error state is an `ErrorMessage`. Hilt's two annotations were the whole
 * distance, and `HiltExploreViewModel` in :feature:explore is where they went.
 *
 * A sibling module rather than a `commonMain` inside :feature:explore, because Android already
 * consumes this shape: `smartphotos.kmp.library` gives it a `jvm` target, and the feature resolves
 * that variant exactly as every module resolves :core:domain's. Converting the feature itself would
 * have meant Android's KMP library plugin under Hilt, KSP and the feature convention, none of which
 * were built around it. The `explore-` prefix is load-bearing: it is how the feature convention's
 * layering check tells a feature's own half from a lateral edge.
 *
 * No tests of its own, for a reason ExploreViewModelTest records: the suite runs in :feature:explore
 * because :core:testing is an Android library no target here can consume.
 */
plugins {
    // Nothing Android, and the same target set as :core:domain -- which is also why that set lost
    // iosX64: this module's lifecycle-viewmodel dependency publishes no variant for it.
    id("smartphotos.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: PhotoRepository, AnalyticsRepository and ErrorHandler are ExploreViewModel's
            // constructor parameters, and HiltExploreViewModel restates them in its own.
            api(project(":core:domain"))

            // api: ExploreViewModel extends ViewModel, so a consumer cannot subclass or call it
            // without resolving this.
            api(libs.androidx.lifecycle.viewmodel)

            // api: `uiState` is a StateFlow. Declared rather than inherited through :core:domain's
            // own api edge, because this module names coroutines itself.
            api(libs.kotlinx.coroutines.core)
        }
    }
}