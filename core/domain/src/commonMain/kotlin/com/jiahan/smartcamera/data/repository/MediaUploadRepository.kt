package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail

/**
 * Data-layer contract for the media pipeline: local preparation, then upload to Storage.
 *
 * Split out of [NoteRepository], which held these three alongside note CRUD and was the only
 * reason that class needed a `Context`, Firebase Storage, the media-file contract and an
 * application-lifetime scope. Nothing here reads or writes a note — [uploadMedia] returns the
 * [MediaDetail] list a caller then hands to [NoteRepository.addNote], and [uploadMediaToCache]
 * is used for profile pictures as well as note media.
 *
 * As in [NoteRepository], every fallible operation returns [Result] so callers never wrap a call
 * in try/catch. [uploadMediaToCache] is the exemption, for the reason its own doc gives.
 */
interface MediaUploadRepository {

    /**
     * Inspects each URI, generating a thumbnail for the videos among them, and returns what
     * [uploadMedia] consumes.
     *
     * Local work only: nothing leaves the device here. A URI that cannot be read is logged and
     * dropped rather than failing the batch, so a single unreadable pick does not lose the rest.
     */
    suspend fun buildLocalMediaDetails(uriList: List<MediaUri>): Result<List<NoteMediaDetail>>

    /**
     * Uploads each prepared item — and its thumbnail, for a video — returning the remote URLs as
     * the [MediaDetail] list a note carries.
     *
     * Items upload in parallel and a failed one is logged and dropped, so the returned list can be
     * shorter than [noteMediaDetailList]. Fails as a whole only when nobody is signed in, since
     * there is no user-scoped path to upload to.
     */
    suspend fun uploadMedia(noteMediaDetailList: List<NoteMediaDetail>): Result<List<MediaDetail>>

    /**
     * Fire-and-forget upload of [uriList] into the cache storage folder: failures are logged
     * internally instead of returned, and files with no content are skipped.
     *
     * Pass `deleteAfterUpload = true` for temporary capture files the caller owns — each one is
     * deleted once its upload is done, whether that upload succeeded, failed, or was skipped.
     * Leave it `false` for URIs the app doesn't own, such as gallery picks.
     */
    suspend fun uploadMediaToCache(
        uriList: List<MediaUri>,
        deleteAfterUpload: Boolean = false
    )
}