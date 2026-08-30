/*
 * Pure-JVM module: domain models, repository contracts, and the few helpers every layer shares.
 *
 * The absence of the Android plugin is the whole point. `import android.*` does not resolve here,
 * so the purity rule that the Separation of concerns and KMP readiness sections of AGENTS.md state
 * in prose is enforced by the compiler instead. Anything that needs a Context, a Uri or a Firebase
 * type belongs in :app (and, once it exists, :core:data) — not here.
 *
 * No Hilt plugin either: @Inject and @Qualifier are plain JSR-330 annotations that need no
 * annotation processing in this module. The @Provides/@Binds that satisfy them stay above.
 */
plugins {
    // Applies the Kotlin JVM plugin -- and nothing Android -- plus the Java/JVM target shared
    // with the other modules. Keeping those in one place is what stops this module's target
    // drifting from :app's, a mismatch that surfaces as an opaque Gradle variant-resolution
    // failure rather than an obvious version error.
    id("smartphotos.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // api rather than implementation for the first three: Flow, LocalDate and @Serializable all
    // appear in the public signatures of the repository interfaces and models below, so consumers
    // compile against them. javax.inject does not surface in a signature — the qualifiers here are
    // our own annotation classes — so it stays implementation, which is the default to prefer.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}