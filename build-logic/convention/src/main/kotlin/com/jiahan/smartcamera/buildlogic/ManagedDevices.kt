package com.jiahan.smartcamera.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTestBuilder
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.Project

/**
 * The emulator CI runs the instrumented tests on, declared in Gradle so nobody has to have one.
 *
 * **This exists because `compileDebugAndroidTestKotlin` was the whole of CI's androidTest story.**
 * Compiling is not running: 103 instrumented tests across eleven modules, and the only thing that
 * ever executed them was somebody remembering to attach a device or run `./scripts/run-test-lab.sh`
 * by hand. Most of them are `sharedTest/` suites that Robolectric also runs on the JVM, so CI did
 * see their assertions -- but fourteen are androidTest-only, and those fourteen are the ones with
 * no JVM half to fall back on: `HiltGraphSmokeTest` (the only check that the real Hilt graph
 * assembles), `SmartPhotosNavigationTest` (the only check that the nav graph works), and the two
 * suites that are device-only because production code sleeps on `Dispatchers.Main`. This build has
 * already paid for that gap once -- four `sharedTest` assertions sat wrong for months because
 * nothing ran them -- and the four that remain are exactly the ones a wrong assertion would hide
 * in.
 *
 * On every Android module rather than on `:app`: eleven modules have androidTest sources and all
 * eleven want the same device. The five Android libraries that have none are handled by
 * [disableAndroidTestWithoutSources] below, because a device-test task with nothing to run does not
 * skip itself.
 *
 * ### Why this device
 *
 * - **API 36**, the app's `targetSdk`, not the oldest API that would boot. The one device-only
 *   failure this repo has actually recorded was API-36-specific: espresso-core 3.5.0 reaching for
 *   `InputManager.getInstance`, removed in 36, which killed 51 tests across seven modules in
 *   `onIdle` before their first assertion. A CI emulator pinned to something older would have
 *   watched all of that go green. `minSdk` 28 is a compile floor, not a test target.
 * - **`google-atd`**, the Automated Test Device image: no Play Store, no system apps, no
 *   animations, roughly half the boot time and memory of a full image, and it is what Google
 *   recommends for exactly this job. `aosp-atd` is smaller still and is the wrong choice here --
 *   `HiltGraphSmokeTest` resolves the real `AppUpdateManager` and lets Firebase auto-initialise, so
 *   the Google APIs have to be present. Both variants publish `x86_64` and `arm64-v8a` at this API,
 *   so the same declaration serves CI and an Apple-silicon laptop.
 *
 * The device name is the task name: `pixel6Api36DebugAndroidTest`. Renaming it changes the CI
 * workflow's command.
 */
internal fun Project.configureManagedDevices(
    commonExtension: CommonExtension,
) {
    with(commonExtension.testOptions) {
        /*
         * Sets the three system animation scales to 0 for the duration of a run, the same thing
         * the testing docs tell you to do by hand in Developer options. A Compose rule syncs on
         * its own clock and does not strictly need it, but the navigation transitions and the
         * bottom bar's spring are real window animations that the *platform* drives, and those are
         * what leave a tap landing on a view that has not settled.
         */
        animationsDisabled = true

        managedDevices.localDevices.create("pixel6Api36").apply {
            device = "Pixel 6"
            apiLevel = 36
            systemImageSource = "google-atd"
        }
    }
}

/**
 * Turns the androidTest component off in a module that has no instrumented test sources.
 *
 * **A device-test task in a module with nothing to run does not skip itself**, which is not what
 * [configureManagedDevices] above assumed. AGP decides whether to launch by asking whether the
 * androidTest *compile output* holds any class file: `AbstractTestDataImpl.hasTests` subtracts the
 * R and BuildConfig jars from the project scope's classes, and the R classes are compiled into that
 * scope's jar as well, so the subtraction never reaches them. Any module that owns a resource --
 * or merely depends on something that does -- therefore looks like it has tests.
 *
 * `:core:common` is where that surfaced. It built an androidTest APK containing its own `R`, Hilt's
 * and half of AndroidX's and nothing else, AGP installed it, and the instrumentation died on
 * `ClassNotFoundException: androidx.test.runner.AndroidJUnitRunner` -- the runner arrives through
 * `androidTestImplementation`, which a module with no instrumented tests has no reason to declare.
 * "Starting 0 tests", then a crash, then a red job: the nine feature suites queued behind it never
 * ran at all, on the first CI run that was supposed to execute them.
 *
 * The test is for source *directories* rather than for compiled output, because that is the half a
 * reader can see: `src/androidTest/` and `src/sharedTest/`, the latter being the one the feature
 * convention compiles into androidTest as well as into the unit tests. A module that grows either
 * starts building an androidTest APK again with no edit here -- but a *third* instrumented source
 * set would have to be added to [INSTRUMENTED_TEST_SOURCE_SETS].
 */
internal fun <VariantBuilderT> Project.disableAndroidTestWithoutSources(
    androidComponents: AndroidComponentsExtension<*, VariantBuilderT, *>,
) where VariantBuilderT : VariantBuilder, VariantBuilderT : HasAndroidTestBuilder {
    val hasInstrumentedTestSources = INSTRUMENTED_TEST_SOURCE_SETS.any { sourceSet ->
        projectDir.resolve("src/$sourceSet").isDirectory
    }
    if (hasInstrumentedTestSources) return

    androidComponents.beforeVariants { variantBuilder ->
        variantBuilder.androidTest.enable = false
    }
}

/** The source sets that compile into androidTest -- see [disableAndroidTestWithoutSources]. */
private val INSTRUMENTED_TEST_SOURCE_SETS = listOf("androidTest", "sharedTest")
