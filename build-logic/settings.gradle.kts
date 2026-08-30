/*
 * Settings for the `build-logic` included build.
 *
 * This is a separate Gradle build, not a subproject of the app: the root `settings.gradle.kts`
 * pulls it in with `includeBuild("build-logic")` from inside `pluginManagement`, which is what
 * lets the modules apply its plugins by id. It is deliberately not `buildSrc` -- a change to
 * `buildSrc` invalidates the whole build's configuration cache, whereas an included build is
 * treated as an ordinary dependency and only the projects that use a changed plugin rebuild.
 */
dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    // The app's own version catalog, reused verbatim. Without this the convention plugins would
    // have to hardcode the AGP and Kotlin versions they compile against, and those would then
    // drift from the versions the modules actually apply.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
