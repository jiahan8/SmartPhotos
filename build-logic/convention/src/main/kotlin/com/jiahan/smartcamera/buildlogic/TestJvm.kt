package com.jiahan.smartcamera.buildlogic

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
