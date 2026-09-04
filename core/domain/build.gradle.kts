/*
 * Kotlin Multiplatform module: domain models, repository contracts, and the few helpers every
 * layer shares. `commonMain` compiles for the JVM the Android app runs on and for the three Apple
 * targets an iOS client would use.
 *
 * The absence of the Android plugin was always this module's point, and multiplatform sharpens
 * that rather than replacing it. `commonMain` compiles against the *intersection* of every
 * declared target, so `import android.*` still does not resolve -- and neither does `java.*` any
 * more, which the Kotlin JVM plugin could never have enforced. The purity rule that the Separation
 * of concerns and KMP readiness sections of AGENTS.md state in prose is held by the compiler, and
 * CI runs `compileCommonMainKotlinMetadata` so it keeps being held on a runner with no Xcode.
 *
 * Anything that needs a Context, a Uri or a Firebase type belongs in :core:common or :core:data,
 * not here. Anything that needs only the JVM goes in `jvmMain`, which is where `di/Qualifiers.kt`
 * now sits: Hilt is Android-only, so its JSR-330 annotations cannot follow the models down into
 * commonMain. Android consumers resolve this module's `jvm` variant and so still see them, which
 * is why that move needed no edit anywhere else in the build.
 */
plugins {
    // Applies kotlin.multiplatform -- and nothing Android -- plus the target set and the JVM
    // target shared with the other modules. Keeping those in one place is what stops this module's
    // target drifting from :app's, a mismatch that surfaces as an opaque Gradle
    // variant-resolution failure rather than an obvious version error.
    id("smartphotos.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api rather than implementation: Flow, LocalDate and @Serializable all appear in the
            // public signatures of the repository interfaces and models here, so consumers compile
            // against them. All three are multiplatform, which is why they needed no change when
            // this module gained the Apple targets.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.core)
        }

        jvmMain.dependencies {
            // Qualifiers.kt alone, and the reason it is the module's one non-common file. It does
            // not surface in a signature -- the qualifiers are our own annotation classes -- so it
            // stays implementation, which is the default to prefer.
            implementation(libs.javax.inject)
        }

        commonTest.dependencies {
            // kotlin-test rather than junit: these tests compile for every target declared above,
            // and org.junit exists on none of them but the JVM.
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}