// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Needed here from the moment `smartphotos.android.feature` started applying serialization by
    // id rather than each feature declaring `alias(libs.plugins.kotlin.serialization)` itself: a
    // convention plugin's `pluginManager.apply("...")` resolves against the build classpath this
    // block establishes, not against the version catalog. Without the line it fails at apply time
    // with "Plugin with id 'org.jetbrains.kotlin.plugin.serialization' not found".
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.roborazzi) apply false
}