package com.jiahan.smartcamera.domain

/**
 * Describes the intended change to a user's profile picture, replacing separate
 * uri/url/delete-flag parameters with a single tri-state value.
 */
sealed interface ProfilePictureUpdate {
    /** No change to the current profile picture. */
    data object Keep : ProfilePictureUpdate

    /** Remove the current profile picture. */
    data object Delete : ProfilePictureUpdate

    /** Set a new profile picture, already uploaded at [url] and locally available at [uri]. */
    data class Set(val uri: MediaUri, val url: String) : ProfilePictureUpdate
}