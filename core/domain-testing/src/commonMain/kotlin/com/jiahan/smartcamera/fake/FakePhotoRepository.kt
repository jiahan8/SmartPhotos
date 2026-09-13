package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.domain.PhotoPage

/**
 * In-memory [PhotoRepository] test double, standing in for the Unsplash-backed implementation.
 *
 * Browse and search results are configured separately, because the screen shows them in different
 * states and a test drives one without disturbing the other. [hasMore] is held per result rather
 * than derived from the list, matching the real repository -- pagination reads the row count the
 * source returned, never the mapped list size.
 *
 * It first existed for ExploreScreenTest, a `sharedTest` suite a mock could not follow into
 * androidTest. ExploreViewModelTest kept mockk until the ViewModel moved to `commonMain`, where the
 * Apple targets have no mockk -- which is what [listAnswer], [searchAnswer] and the request logs are
 * for: a per-page stub, a call held in flight, and a `coVerify`, as plain Kotlin.
 */
class FakePhotoRepository : PhotoRepository {

    var listResult: Result<PhotoPage> = Result.success(PhotoPage(emptyList(), hasMore = false))
    var searchResult: Result<PhotoPage> = Result.success(PhotoPage(emptyList(), hasMore = false))

    /**
     * Answers [listPhotos] per call, for a result that depends on the page or a call that has to
     * suspend. Null falls back to [listResult].
     */
    var listAnswer: (suspend (page: Int) -> Result<PhotoPage>)? = null

    /** As [listAnswer], for [searchPhotos]. Null falls back to [searchResult]. */
    var searchAnswer: (suspend (query: String, page: Int) -> Result<PhotoPage>)? = null

    /** Every page [listPhotos] was asked for, in order, recorded before the answer runs. */
    val requestedListPages = mutableListOf<Int>()

    /**
     * Every query and page [searchPhotos] was asked for, in order, recorded before the answer runs.
     */
    val requestedSearches = mutableListOf<Pair<String, Int>>()

    /** Stubs a successful browse page. `hasMore` drives pagination independently of list size. */
    fun setPhotos(photos: List<Photo>, hasMore: Boolean = false) {
        listResult = Result.success(PhotoPage(photos, hasMore))
    }

    /** Stubs a successful search page, the result [searchPhotos] returns for any query. */
    fun setSearchPhotos(photos: List<Photo>, hasMore: Boolean = false) {
        searchResult = Result.success(PhotoPage(photos, hasMore))
    }

    override suspend fun listPhotos(page: Int, pageSize: Int): Result<PhotoPage> {
        requestedListPages += page
        return listAnswer?.invoke(page) ?: listResult
    }

    override suspend fun searchPhotos(
        query: String,
        page: Int,
        pageSize: Int
    ): Result<PhotoPage> {
        requestedSearches += query to page
        return searchAnswer?.invoke(query, page) ?: searchResult
    }
}