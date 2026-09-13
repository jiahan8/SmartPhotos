package com.jiahan.smartcamera.buildlogic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.withType

/**
 * Pins the unit-test JVM's timezone and locale, and trims test failure output.
 *
 * The pin was in `app/build.gradle.kts` and is here for the reason AGENTS.md gives:
 * `Long.toFormattedDateTime()` resolves the zone and locale at render time, so any Roborazzi
 * golden containing a note timestamp renders differently per machine -- green on a UTC+8 laptop,
 * red on the UTC CI runner. Now that screenshot tests exist in more than one module the pin has to
 * hold in more than one module, and applying it from here is the alternative to copying it.
 *
 * The logging half does two opposite jobs on purpose: FULL restores the assertion message that the
 * default SHORT format collapses away (which golden changed, and where its comparison image went),
 * while `showStackTraces = false` drops the ~20 lines of Roborazzi-internal frames FULL would
 * otherwise append per failure.
 */
internal fun Project.configureTestJvm() {
    tasks.withType<Test>().configureEach {
        systemProperty("user.timezone", "UTC")
        systemProperty("user.language", "en")
        systemProperty("user.country", "US")

        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
            showStackTraces = false
        }
    }
}

/**
 * Turns the unit-test component off in a module that has no JVM test sources -- the host-test twin
 * of [disableAndroidTestWithoutSources], for the same reason: a test task with nothing to run does
 * not skip itself.
 *
 * `:feature:profile` is where that surfaced. `ProfileViewModelTest` moved to
 * `:feature:profile-viewmodel`'s `commonTest`, leaving the module only its androidTest-only
 * `ProfileScreenTest`, and `testDebugUnitTest` then failed with "There are test sources present and
 * no filters are applied, but the test task did not discover any tests to execute". The unit-test
 * compile had produced no class at all -- only merged resources and an `R.jar` -- which is enough
 * for Gradle 9's `failOnNoDiscoveredTests` to call it a misconfiguration, and fail the unqualified
 * `testDebugUnitTest` CI runs.
 *
 * Switching the component off rather than setting `failOnNoDiscoveredTests = false` keeps that
 * check for every module that does have tests, where it catches a real misconfiguration. The test
 * is for source *directories*, like its twin: `src/test/` and the feature convention's
 * `src/sharedTest/`, so a module that grows either gets its task back with no edit here.
 */
internal fun <VariantBuilderT> Project.disableUnitTestWithoutSources(
    androidComponents: AndroidComponentsExtension<*, VariantBuilderT, *>,
) where VariantBuilderT : VariantBuilder, VariantBuilderT : HasHostTestsBuilder {
    val hasUnitTestSources = UNIT_TEST_SOURCE_SETS.any { sourceSet ->
        projectDir.resolve("src/$sourceSet").isDirectory
    }
    if (hasUnitTestSources) return

    androidComponents.beforeVariants { variantBuilder ->
        variantBuilder.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

/** The source sets that compile into the unit tests -- see [disableUnitTestWithoutSources]. */
private val UNIT_TEST_SOURCE_SETS = listOf("test", "sharedTest")
