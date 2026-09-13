/*
 * Kotlin Multiplatform half of :feature:settings: SettingsViewModel and its suite.
 *
 * Moved to `commonMain` alongside AuthViewModel, in ExploreViewModel's shape --
 * :feature:explore-viewmodel's build file and ARCHITECTURE.md's Kotlin Multiplatform section record
 * why. Everything this ViewModel reaches for is in :core:domain -- its repositories, the new-password
 * validator, ErrorMessage -- so the convention supplies every dependency.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}