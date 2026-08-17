package com.jiahan.smartcamera.note

import android.net.Uri
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

data class ShareContent(val text: String?, val uris: List<Uri>)

/**
 * Scoped per ViewModel like [NoteActionsDelegate]: this owns a single [shareEvent] stream meant to
 * be shared by whatever depends on it within one ViewModel. No current dependent double-injects it,
 * but without this scope a future one could reintroduce the same silently-dropped-event bug that
 * [NoteActionsDelegate]'s scoping fixes.
 */
@ViewModelScoped
class NoteShareDelegate @Inject constructor(
    private val mediaFileRepository: MediaFileRepository,
    private val noteActions: NoteActionsDelegate,
    private val resourceProvider: ResourceProvider
) {
    private val _shareEvent = MutableSharedFlow<ShareContent>(extraBufferCapacity = 1)
    val shareEvent = _shareEvent.asSharedFlow()

    suspend fun shareNote(note: HomeNote) {
        val mediaList = note.mediaList.orEmpty()
        val uris = coroutineScope {
            mediaList
                .map { media ->
                    async {
                        val url = if (media.isVideo) media.videoUrl else media.photoUrl
                        url?.let { mediaFileRepository.downloadToCacheFile(it, media.isVideo) }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }

        if (mediaList.isNotEmpty() && uris.isEmpty()) {
            noteActions.reportError(resourceProvider.getString(R.string.share_note_failure))
            return
        }

        _shareEvent.emit(ShareContent(text = note.text, uris = uris))
    }
}