pluginManagement {
    // The convention plugins in `build-logic`. It is an included *build*, not a subproject, so it
    // resolves and compiles before this build's projects are configured -- which is what lets
    // `plugins { id("smartphotos.android.library") }` work by id in a module build file.
    includeBuild("build-logic")

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
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The same group filter `pluginManagement` above applies, and for the same reason: without
        // it, `google()` being first means every artifact that lives on Maven Central -- junit,
        // mockk, robolectric, turbine, coil, okhttp, all of kotlinx -- is looked up against
        // dl.google.com and 404s before the build falls through. Google's repo hosts only these
        // three group prefixes, so anything else asking it is a wasted round trip per artifact.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Smart Camera"
include(":app")
include(":core:domain")
include(":core:common")
include(":core:data")
include(":core:ui")
include(":core:testing")
include(":core:screenshot-testing")
include(":feature:auth")
include(":feature:explore")
include(":feature:favorite")
include(":feature:home")
include(":feature:note")
include(":feature:preview")
include(":feature:profile")
include(":feature:search")
include(":feature:settings")