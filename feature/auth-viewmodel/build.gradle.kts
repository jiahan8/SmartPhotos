/*
 * Kotlin Multiplatform half of :feature:auth: AuthViewModel and its suite.
 *
 * The second ViewModel moved to `commonMain`, after ExploreViewModel, and the same shape --
 * :feature:explore-viewmodel's build file and ARCHITECTURE.md's Kotlin Multiplatform section record
 * why. Everything this ViewModel reaches for is in :core:domain -- its repositories, the username
 * and display-name validators, ErrorMessage -- so the convention supplies every dependency.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}