import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.jiahan.smartcamera.buildlogic"

/*
 * 17, not the 11 the app modules target. This code runs inside the Gradle daemon rather than on a
 * device, so it follows Gradle's own floor (Gradle 9 requires 17+) and has nothing to do with the
 * app's minSdk or desugaring. Don't "fix" it to match the modules.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly throughout: these plugins are already on the build classpath of any project that
    // applies them. Declaring them `implementation` would put a second copy of AGP on the
    // classpath and fail at apply time.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    // No compose-compiler-gradle-plugin: AndroidComposeConventionPlugin applies that plugin by id
    // and then touches only AGP's own `buildFeatures.compose`, so none of its DSL types are
    // referenced at compile time. Add it back if a convention ever configures the Compose compiler
    // itself (metrics/reports destinations, strong-skipping flags).
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "smartphotos.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "smartphotos.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "smartphotos.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "smartphotos.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "smartphotos.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
