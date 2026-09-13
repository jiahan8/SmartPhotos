/*
 * Kotlin Multiplatform half of :feature:favorite: FavoriteViewModel and its suite.
 *
 * Moved together with Home and Search, once the note delegates all three inject had gone to
 * :core:domain and nothing but Hilt's annotations tied them to Android. Everything it reaches for
 * is in :core:domain, so the convention supplies every dependency.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}
