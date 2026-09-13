/*
 * Kotlin Multiplatform half of :feature:profile: ProfileViewModel and its suite.
 *
 * The last feature ViewModel to move, together with NoteViewModel, and the two waited for the same
 * reason: each held the `android.net.Uri` MediaFileRepository.createPhotoUri() returned -- a capture
 * destination kept in state until the camera came back. That method is MediaCaptureRepository's
 * now, in :core:domain, returning a MediaUri, and ProfileScreen converts at the launcher. With no
 * route to decode, HiltProfileViewModel adds the annotations and nothing else, and the convention
 * supplies every dependency here.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}
