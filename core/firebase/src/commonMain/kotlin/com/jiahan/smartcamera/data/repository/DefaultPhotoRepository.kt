package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.domain.PhotoPage
import com.jiahan.smartcamera.util.safeCall
import dev.gitlive.firebase.functions.FirebaseFunctions

/**
 * [PhotoRepository] over the two Unsplash-backed Cloud Functions, on GitLive's multiplatform
 * Firebase SDK.
 *
 * The class is a reader of a loosely-shaped payload: a photo that loses its author or its dimensions
 * still renders, just wrongly, so nearly every field has a fallback. GitLive only hands a callable's
 * result back through kotlinx.serialization, so the payload is read into [UnsplashPayload] -- every
 * field optional and defaulted -- rather than cast out of nested maps as it was on the Android SDK.
 *
 * The Firebase call itself sits behind [UnsplashCallable], so the mapping is testable in
 * `commonTest` against GitLive's real decoder without a Firebase instance.
 */
class DefaultPhotoRepository internal constructor(
    private val callable: UnsplashCallable,
) : PhotoRepository {

    constructor(functions: FirebaseFunctions) : this(GitLiveUnsplashCallable(functions))

    internal companion object {
        const val FUNCTION_LIST_UNSPLASH_PHOTOS = "listUnsplashPhotos"
        const val FUNCTION_SEARCH_UNSPLASH_PHOTOS = "searchUnsplashPhotos"
    }

    override suspend fun listPhotos(page: Int, pageSize: Int): Result<PhotoPage> = safeCall {
        callable.call(FUNCTION_LIST_UNSPLASH_PHOTOS, UnsplashArgs(page = page, perPage = pageSize))
            .toPhotoPage(pageSize)
    }

    override suspend fun searchPhotos(
        query: String,
        page: Int,
        pageSize: Int
    ): Result<PhotoPage> = safeCall {
        callable.call(
            FUNCTION_SEARCH_UNSPLASH_PHOTOS,
            UnsplashArgs(query = query, page = page, perPage = pageSize),
        ).toPhotoPage(pageSize)
    }
}

/**
 * `hasMore` counts the rows the callable returned, not the parsed photos: [toPhoto] drops a
 * malformed entry, and a short parsed list would otherwise be read as "end of feed" and stop
 * pagination for the rest of the session.
 */
private fun UnsplashPayload?.toPhotoPage(pageSize: Int): PhotoPage {
    val rows = this?.photos.orEmpty()
    return PhotoPage(
        photos = rows.mapNotNull { it?.toPhoto() },
        hasMore = rows.size >= pageSize,
    )
}

private fun UnsplashRow.toPhoto(): Photo? {
    val id = id ?: return null
    val urls = urls ?: return null
    val photoUrl = urls.regular ?: urls.full ?: urls.raw ?: return null
    return Photo(
        id = id,
        description = description ?: altDescription,
        photoUrl = photoUrl,
        thumbnailUrl = urls.small ?: urls.thumb ?: photoUrl,
        width = width,
        height = height,
        color = color,
        likes = likes,
        username = user?.name ?: user?.username ?: "",
        profilePictureUrl = user?.profileImage?.small,
    )
}