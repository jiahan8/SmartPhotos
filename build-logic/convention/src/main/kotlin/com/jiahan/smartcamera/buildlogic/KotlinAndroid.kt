package com.jiahan.smartcamera.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * The SDK levels and JVM target shared by every Android module, applied through [CommonExtension]
 * so the application and library plugins configure them identically.
 *
 * Written as property assignment rather than the `defaultConfig { }` / `compileOptions { }` block
 * form the module build files use. Those block forms are declared on the concrete
 * `ApplicationExtension` and `LibraryExtension` -- each taking its own flavor of the nested type --
 * and AGP 9's [CommonExtension] carries only the getters. Property access is what both concrete
 * extensions have in common, which is the whole point of configuring through the shared supertype.
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    with(commonExtension) {
        compileSdk = ProjectConfig.COMPILE_SDK
        defaultConfig.minSdk = ProjectConfig.MIN_SDK

        compileOptions.sourceCompatibility = ProjectConfig.JAVA_VERSION
        compileOptions.targetCompatibility = ProjectConfig.JAVA_VERSION
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(ProjectConfig.JVM_TARGET)
        }
    }
}
