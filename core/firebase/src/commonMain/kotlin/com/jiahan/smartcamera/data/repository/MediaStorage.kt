package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaUri
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage

/**
 * Cloud Storage uploads, for [DefaultUserRepository]'s profile pictures and
 * [DefaultMediaUploadRepository]'s note media: their seam onto Storage.
 *
 * The URL is a call of its own because the cache upload never asks for one.
 */
internal interface MediaStorage {
    /** Uploads the local file at [file] to [path]. */
    suspend fun putFile(path: String, file: MediaUri)

    /** The download URL of the object at [path]. */
    suspend fun getDownloadUrl(path: String): String
}

/**
 * GitLive's Storage, on the bucket Remote Config names, built on first use as the Android SDK
 * repositories' instances were.
 */
internal class GitLiveMediaStorage(
    private val remoteConfigRepository: RemoteConfigRepository,
) : MediaStorage {

    private val storage: FirebaseStorage by lazy {
        Firebase.storage(remoteConfigRepository.getStorageUrl())
    }

    override suspend fun putFile(path: String, file: MediaUri) {
        storage.reference.child(path).putFile(file.toStorageFile())
    }

    override suspend fun getDownloadUrl(path: String): String =
        storage.reference.child(path).getDownloadUrl()
}

/** The platform file GitLive's `putFile` takes, for a location carried as its URI string. */
internal expect fun MediaUri.toStorageFile(): File