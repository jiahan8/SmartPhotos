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
 * ExploreViewModelTest is in this module's `commonTest`, so it runs on the JVM in CI and on an iOS
 * simulator on a Mac. It came with its subject once the fakes it needed moved to
 * :core:domain-testing -- they had sat in :core:testing, an Android library no target here can
 * consume.
 */
plugins {
    // Everything this module declared by hand while it was the only ViewModel module -- the
    // :core:domain, lifecycle-viewmodel and coroutines api edges, and the commonTest set -- moved
    // into this convention when :feature:auth-viewmodel and :feature:settings-viewmodel wanted
    // the same lines. The reasons for each are recorded there.
    id("smartphotos.kmp.viewmodel")
}