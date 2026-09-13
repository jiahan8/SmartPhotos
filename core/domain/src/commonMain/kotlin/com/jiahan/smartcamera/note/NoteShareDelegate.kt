package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.Note
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What the share sheet needs: the note's text and the media downloaded for it. [uris] are
 * [MediaUri]s, which the screen turns back into platform URIs as it builds the intent.
 */
data class OutgoingShare(val text: String?, val uris: List<MediaUri>)

/**
 * Sharing a note: download its media to cache files in parallel, then emit what the chooser needs.
 *
 * It sits below the screens that call it because all four of them do -- home, search, favorite and
 * preview -- and shared code goes down rather than sideways. It is the one piece of `note/` that did
 * not dissolve when the Room mirror retired NoteHandler: the actions delegate was two lines of
 * repository call and inlined, this is thirty of parallel download and did not.
 *
 * In `commonMain` since [OutgoingShare] stopped carrying `android.net.Uri`. One instance per
 * ViewModel like [NoteErrorReporter], and provided the same way: this owns a single [shareEvent]
 * stream meant to be shared by whatever depends on it within one ViewModel. No current dependent
 * double-injects it, but a second instance would reintroduce the silently-dropped-event bug that
 * [NoteErrorReporter]'s scoping exists to prevent.
 */
class NoteShareDelegate(
    private val mediaCacheRepository: MediaCacheRepository,
    private val noteErrorReporter: NoteErrorReporter
) {
    private val _shareEvent = MutableSharedFlow<OutgoingShare>(extraBufferCapacity = 1)
    val shareEvent = _shareEvent.asSharedFlow()

    suspend fun shareNote(note: Note) {
        val mediaList = note.mediaList.orEmpty()
        val uris = coroutineScope {
            mediaList
                .map { media ->
                    async {
                        val url = if (media.isVideo) media.videoUrl else media.photoUrl
                        url?.let { mediaCacheRepository.downloadToCacheFile(it, media.isVideo) }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }

        if (mediaList.isNotEmpty() && uris.isEmpty()) {
            noteErrorReporter.reportShareFailure()
            return
        }

        _shareEvent.emit(OutgoingShare(text = note.text, uris = uris))
    }
}