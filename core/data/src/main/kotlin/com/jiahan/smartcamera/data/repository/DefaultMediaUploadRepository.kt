package com.jiahan.smartcamera.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.di.ApplicationScope
import com.jiahan.smartcamera.di.IoDispatcher
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.createVideoThumbnail
import com.jiahan.smartcamera.util.safeCall
import com.jiahan.smartcamera.util.toMediaUri
import com.jiahan.smartcamera.util.toPlatformUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

class DefaultMediaUploadRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val authRepository: AuthRepository,
    private val mediaFileRepository: MediaFileRepository,
    private val errorHandler: ErrorHandler,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaUploadRepository {

    private val storage: FirebaseStorage by lazy {
        Firebase.storage(remoteConfigRepository.getStorageUrl())
    }
    private val storageFolder: String by lazy { remoteConfigRepository.getStorageFolderName() }
    private val cacheStorageFolder: String by lazy { remoteConfigRepository.getStorageCacheFolderName() }

    private fun userScopedPath(folder: String, userId: String, fileName: String) =
        "$folder/$userId/$fileName"

    override suspend fun buildLocalMediaDetails(
        uriList: List<MediaUri>
    ): Result<List<NoteMediaDetail>> =
        safeCall {
            withContext(ioDispatcher) {
                uriList.mapNotNull { mediaUri ->
                    safeCall {
                        val uri = mediaUri.toPlatformUri()
                        val isVideo = mediaFileRepository.isVideoUri(uri)
                        val thumbnailUri = if (isVideo) {
                            createVideoThumbnail(context, uri)
                                ?.let { mediaFileRepository.saveBitmapAsTempFile(it) }
                                ?.toMediaUri()
                        } else null
                        NoteMediaDetail(
                            photoUri = if (!isVideo) mediaUri else null,
                            videoUri = if (isVideo) mediaUri else null,
                            thumbnailUri = thumbnailUri,
                            isVideo = isVideo
                        )
                    }.onFailure(errorHandler::logError).getOrNull()
                }
            }
        }

    override suspend fun uploadMedia(
        noteMediaDetailList: List<NoteMediaDetail>
    ): Result<List<MediaDetail>> = safeCall {
        val userId = authRepository.currentUserId
            ?: throw AppError.NotAuthenticated()
        coroutineScope {
            noteMediaDetailList.map { noteMediaDetail ->
                async(ioDispatcher) {
                    safeCall {
                        val mediaId = Uuid.random().toString()
                        val extension =
                            if (noteMediaDetail.isVideo) EXTENSION_MP4 else EXTENSION_JPG
                        val storageRef =
                            storage.reference.child(
                                userScopedPath(storageFolder, userId, "$mediaId$extension")
                            )

                        val mediaUri = noteMediaDetail.photoUri ?: noteMediaDetail.videoUri
                        ?: throw AppError.NoMediaAvailable()

                        storageRef.putFile(mediaUri.toPlatformUri()).await()
                        val mediaUrl = storageRef.downloadUrl.await().toString()

                        val thumbnailUrl = noteMediaDetail.thumbnailUri?.let { thumbUri ->
                            val thumbnailId = PREFIX_THUMBNAIL + Uuid.random().toString()
                            val thumbnailRef =
                                storage.reference.child(
                                    userScopedPath(
                                        storageFolder,
                                        userId,
                                        "$thumbnailId$EXTENSION_JPG"
                                    )
                                )
                            thumbnailRef.putFile(thumbUri.toPlatformUri()).await()
                            thumbnailRef.downloadUrl.await().toString()
                        }

                        MediaDetail(
                            photoUrl = if (!noteMediaDetail.isVideo) mediaUrl else null,
                            videoUrl = if (noteMediaDetail.isVideo) mediaUrl else null,
                            thumbnailUrl = thumbnailUrl,
                            isVideo = noteMediaDetail.isVideo
                        )
                    }.onFailure(errorHandler::logError).getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun uploadMediaToCache(
        uriList: List<MediaUri>,
        deleteAfterUpload: Boolean
    ) {
        val userId = authRepository.currentUserId
        uriList.forEach { mediaUri ->
            applicationScope.launch(ioDispatcher) {
                val uri = mediaUri.toPlatformUri()
                if (userId != null && mediaFileRepository.hasContent(uri)) {
                    safeCall {
                        val mediaId = Uuid.random().toString()
                        val storageRef = storage.reference.child(
                            userScopedPath(cacheStorageFolder, userId, mediaId)
                        )
                        storageRef.putFile(uri).await()
                    }.onFailure(errorHandler::logError)
                }
                if (deleteAfterUpload) mediaFileRepository.deleteFile(uri)
            }
        }
    }
}