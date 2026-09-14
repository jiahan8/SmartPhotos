package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaUri
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage

/** Uploads a local file to Cloud Storage: [DefaultUserRepository]'s seam onto Storage. */
internal interface ProfilePictureStorage {
    /** Uploads [file] to [path] and returns its download URL. */
    suspend fun upload(path: String, file: MediaUri): String
}

/**
 * GitLive's Storage, on the bucket Remote Config names, built on first use as the Android SDK
 * repository's instance was.
 */
internal class GitLiveProfilePictureStorage(
    private val remoteConfigRepository: RemoteConfigRepository,
) : ProfilePictureStorage {

    private val storage: FirebaseStorage by lazy {
        Firebase.storage(remoteConfigRepository.getStorageUrl())
    }

    override suspend fun upload(path: String, file: MediaUri): String {
        val reference = storage.reference.child(path)
        reference.putFile(file.toStorageFile())
        return reference.getDownloadUrl()
    }
}

/** The platform file GitLive's `putFile` takes, for a location carried as its URI string. */
internal expect fun MediaUri.toStorageFile(): File