package com.jiahan.smartcamera.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultPhotoRepository @Inject constructor(
    private val functions: FirebaseFunctions
) : PhotoRepository {

    companion object {
        private const val FUNCTION_LIST_UNSPLASH_PHOTOS = "listUnsplashPhotos"
        private const val FIELD_PAGE = "page"
        private const val FIELD_PER_PAGE = "perPage"
        private const val FIELD_PHOTOS = "photos"
        private const val FIELD_ID = "id"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_ALT_DESCRIPTION = "alt_description"
        private const val FIELD_WIDTH = "width"
        private const val FIELD_HEIGHT = "height"
        private const val FIELD_COLOR = "color"
        private const val FIELD_LIKES = "likes"
        private const val FIELD_URLS = "urls"
        private const val FIELD_URL_REGULAR = "regular"
        private const val FIELD_URL_FULL = "full"
        private const val FIELD_URL_RAW = "raw"
        private const val FIELD_URL_SMALL = "small"
        private const val FIELD_URL_THUMB = "thumb"
        private const val FIELD_USER = "user"
        private const val FIELD_USER_NAME = "name"
        private const val FIELD_USER_USERNAME = "username"
        private const val FIELD_USER_PROFILE_IMAGE = "profile_image"
    }

    override suspend fun listPhotos(page: Int, pageSize: Int): Result<List<Photo>> = safeCall {
        val result = functions.getHttpsCallable(FUNCTION_LIST_UNSPLASH_PHOTOS)
            .call(hashMapOf(FIELD_PAGE to page, FIELD_PER_PAGE to pageSize))
            .await()
        val photos = (result.data as? Map<*, *>)?.get(FIELD_PHOTOS) as? List<*> ?: emptyList<Any?>()
        photos.mapNotNull { (it as? Map<*, *>)?.let(::parsePhoto) }
    }

    private fun parsePhoto(map: Map<*, *>): Photo? {
        val id = map[FIELD_ID] as? String ?: return null
        val urls = map[FIELD_URLS] as? Map<*, *> ?: return null
        val imageUrl =
            (urls[FIELD_URL_REGULAR] ?: urls[FIELD_URL_FULL] ?: urls[FIELD_URL_RAW]) as? String
                ?: return null
        val thumbUrl = (urls[FIELD_URL_SMALL] ?: urls[FIELD_URL_THUMB]) as? String ?: imageUrl
        val user = map[FIELD_USER] as? Map<*, *>
        val profileImage = user?.get(FIELD_USER_PROFILE_IMAGE) as? Map<*, *>
        return Photo(
            id = id,
            description = map[FIELD_DESCRIPTION] as? String
                ?: map[FIELD_ALT_DESCRIPTION] as? String,
            imageUrl = imageUrl,
            thumbUrl = thumbUrl,
            width = (map[FIELD_WIDTH] as? Number)?.toInt() ?: 0,
            height = (map[FIELD_HEIGHT] as? Number)?.toInt() ?: 0,
            color = map[FIELD_COLOR] as? String,
            likes = (map[FIELD_LIKES] as? Number)?.toInt() ?: 0,
            username = (user?.get(FIELD_USER_NAME) as? String)
                ?: (user?.get(FIELD_USER_USERNAME) as? String) ?: "",
            userProfileImageUrl = profileImage?.get(FIELD_URL_SMALL) as? String
        )
    }
}