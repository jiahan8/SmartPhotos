package com.jiahan.smartcamera.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The `libs` version catalog, reached from inside a convention plugin.
 *
 * A module's `build.gradle.kts` gets a generated, type-safe `libs.androidx.material3` accessor.
 * Convention plugins do not -- they are ordinary Kotlin compiled against the Gradle API, not
 * scripts, so the generated accessors are not on their classpath. The catalog has to be looked up
 * by name at apply time and its entries reached as strings, which is why an alias typo here is a
 * runtime failure in `:feature:*`'s configuration rather than a compile error in `build-logic`.
 *
 * `build-logic/settings.gradle.kts` is what makes this resolvable at all: it re-declares the app's
 * `gradle/libs.versions.toml` inside the included build.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")