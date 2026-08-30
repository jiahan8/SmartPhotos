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
        google()
        mavenCentral()
    }
}

rootProject.name = "Smart Camera"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:testing")
include(":feature:explore")