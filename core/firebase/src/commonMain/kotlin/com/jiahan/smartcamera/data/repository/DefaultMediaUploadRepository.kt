package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * [MediaUploadRepository] on GitLive's multiplatform Storage, behind [MediaStorage] so its suite runs
 * in `commonTest`.
 *
 * The local half of the pipeline -- telling a video from a photo, extracting its thumbnail, checking
 * a capture has bytes, deleting a temp file -- is Android work, and reaches this class through
 * [MediaFileRepository], which `DefaultMediaFileRepository` implements in :core:data.
 */
class DefaultMediaUploadRepository internal constructor(
    private val storage: MediaStorage,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val authRepository: AuthRepository,
    private val mediaFileRepository: MediaFileRepository,
    private val errorHandler: ErrorHandler,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : MediaUploadRepository {

    constructor(
        remoteConfigRepository: RemoteConfigRepository,
        authRepository: AuthRepository,
        mediaFileRepository: MediaFileRepository,
        errorHandler: ErrorHandler,
        applicationScope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        GitLiveMediaStorage(remoteConfigRepository),
        remoteConfigRepository,
        authRepository,
        mediaFileRepository,
        errorHandler,
        applicationScope,
        ioDispatcher,
    )

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
                        val isVideo = mediaFileRepository.isVideoUri(mediaUri)
                        val thumbnailUri =
                            if (isVideo) mediaFileRepository.createVideoThumbnail(mediaUri) else null
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

                        val mediaUri = noteMediaDetail.photoUri ?: noteMediaDetail.videoUri
                        ?: throw AppError.NoMediaAvailable()

                        val mediaUrl = upload(
                            userScopedPath(storageFolder, userId, "$mediaId$extension"),
                            mediaUri
                        )

                        val thumbnailUrl = noteMediaDetail.thumbnailUri?.let { thumbUri ->
                            val thumbnailId = PREFIX_THUMBNAIL + Uuid.random().toString()
                            upload(
                                userScopedPath(storageFolder, userId, "$thumbnailId$EXTENSION_JPG"),
                                thumbUri
                            )
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
                if (userId != null && mediaFileRepository.hasContent(mediaUri)) {
                    safeCall {
                        val mediaId = Uuid.random().toString()
                        storage.putFile(userScopedPath(cacheStorageFolder, userId, mediaId), mediaUri)
                    }.onFailure(errorHandler::logError)
                }
                if (deleteAfterUpload) mediaFileRepository.deleteFile(mediaUri)
            }
        }
    }

    /** Uploads [file] to [path] and returns its download URL. */
    private suspend fun upload(path: String, file: MediaUri): String {
        storage.putFile(path, file)
        return storage.getDownloadUrl(path)
    }
}