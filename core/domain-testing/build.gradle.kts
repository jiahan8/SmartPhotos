/*
 * Kotlin Multiplatform test fixtures: the fakes for :core:domain's contracts, and NoteMirror.
 *
 * The half of :core:testing a multiplatform module's `commonTest` can consume. ExploreViewModel
 * moved to :feature:explore-viewmodel's commonMain and at first its suite could not follow, because
 * every fixture it would need sat in :core:testing -- an Android library, which no JVM or Apple
 * target can depend on. Nine of those fakes and NoteMirror were already plain Kotlin over
 * :core:domain interfaces, so they moved here unchanged and in the same package.
 *
 * What stayed in :core:testing is what a platform binds: MainDispatcherRule, a JUnit rule.
 * FakeMediaFileRepository stayed with it while its contract carried Android's Uri and Bitmap, and
 * came here once the contract took MediaUris. :core:testing takes this module as `api`, so every
 * Android test that took the fakes from there still does, with no import changed.
 *
 * Consumed by tests only -- `commonTest` directly, `testImplementation` and
 * `androidTestImplementation` through :core:testing -- with no tests of its own. The fixtures rules
 * in AGENTS.md apply as they do to :core:testing: `api` throughout, and no :core:data edge.
 */
plugins {
    id("smartphotos.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: every fake *is* one of :core:domain's interfaces, so a test holding one resolves
            // that interface -- the argument :core:testing makes for its own api edges.
            api(project(":core:domain"))

            // api, and declared rather than inherited through :core:domain's own api edges: the
            // fakes name StateFlow and LocalDate themselves, in the signatures they override.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
    }
}