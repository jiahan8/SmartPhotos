/*
 * `buildlogic`, not `build`. A package directory literally named `build` is matched by the
 * unanchored `build/` pattern that is the reflex entry in a .gitignore, so these three files were
 * silently untracked until it was noticed -- a fresh clone could not have configured the build at
 * all. The name matches this project's `group` for the same reason.
 */
package com.jiahan.smartcamera.buildlogic

import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The values that used to be copied into every module's `build.gradle.kts`.
 *
 * They were duplicated across `:app` and `:core:data` verbatim, and `:core:domain` carried the
 * Java/JVM-target half of them. `:core:ui` would have been the third full copy, which is the
 * trigger this whole included build exists to answer.
 */
internal object ProjectConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 28

    val JAVA_VERSION = JavaVersion.VERSION_11
    val JVM_TARGET = JvmTarget.JVM_11
}
