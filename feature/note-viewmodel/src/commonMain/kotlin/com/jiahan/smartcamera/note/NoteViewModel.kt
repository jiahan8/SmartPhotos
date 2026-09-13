package com.jiahan.smartcamera.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.MediaCaptureRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_MEDIA_ITEMS
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_TEXT_LENGTH
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ErrorTag
import com.jiahan.smartcamera.util.toErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UploadStatus {
    data object Idle : UploadStatus
    data object Uploading : UploadStatus
    data object Success : UploadStatus
    data class Error(val message: ErrorMessage) : UploadStatus

    /** More media was picked than a note holds, and the extra was dropped. Nothing was uploaded. */
    data object MediaLimitReached : UploadStatus
}

data class NoteUiState(
    val noteText: String = "",
    val isNoteTextTooLong: Boolean = false,
    val photoUri: MediaUri? = null,
    val videoUri: MediaUri? = null,
    val mediaList: List<NoteMediaDetail> = emptyList(),
    val uploadStatus: UploadStatus = UploadStatus.Idle
)

/**
 * Backs the note composer.
 *
 * Open and annotation-free so it can live in `commonMain`; `HiltNoteViewModel` in :feature:note is
 * what Hilt builds, and it takes the share waiting on `IncomingShareHandler` and passes it here as
 * [pendingShare]. Every media location this class holds is a [MediaUri] -- a picked item, a shared
 * one, and the capture destination [createPhotoUri]/[createVideoUri] return -- which NoteScreen
 * converts at its picker and camera launchers.
 */
open class NoteViewModel(
    private val noteRepository: NoteRepository,
    private val mediaUploadRepository: MediaUploadRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val mediaCaptureRepository: MediaCaptureRepository,
    pendingShare: IncomingShare?,
    private val errorHandler: ErrorHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        pendingShare?.let { share ->
            if (!share.text.isNullOrBlank()) updateNoteText(share.text)
            if (share.uris.isNotEmpty()) addMedia(share.uris)
        }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    val saveButtonEnabled = _uiState
        .map { state ->
            state.uploadStatus !is UploadStatus.Uploading &&
                    (state.noteText.isNotBlank() || state.mediaList.isNotEmpty()) &&
                    !state.isNoteTextTooLong
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val userPreferences = userPreferencesRepository.userPreferences
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = UserPreferences(
                isDarkTheme = false,
                username = "",
                profilePictureUrl = null
            )
        )

    val username = userPreferences
        .map { it.username }
        .distinctUntilChanged()

    val profilePictureUrl = userPreferences
        .map { it.profilePictureUrl }
        .distinctUntilChanged()

    fun saveNote() {
        val text = _uiState.value.noteText.trim().ifBlank { null }
        val media = _uiState.value.mediaList
        viewModelScope.launch {
            _uiState.update { it.copy(uploadStatus = UploadStatus.Uploading) }
            mediaUploadRepository.uploadMedia(media)
                .onSuccess { mediaDetailList ->
                    noteRepository.addNote(
                        Note(
                            noteId = "",
                            text = text,
                            mediaList = mediaDetailList,
                            username = ""
                        )
                    )
                        .onSuccess {
                            // No event: addNote reads the created note back into the `notes`
                            // table, and every feed renders that.
                            _uiState.update { it.copy(uploadStatus = UploadStatus.Success) }
                        }
                        .onFailure { e -> handleUploadFailure(e) }
                }
                .onFailure { e -> handleUploadFailure(e) }
        }
    }

    // No mapper here any more: addNote folds createNote's validation reasons into AppError, and
    // toErrorMessage carries those up as ErrorMessage.Known, with the Firebase type left in the
    // data layer where it belongs.
    private fun handleUploadFailure(e: Throwable) {
        errorHandler.logError(e)
        _uiState.update { it.copy(uploadStatus = UploadStatus.Error(e.toErrorMessage())) }
    }

    fun resetUploadStatus() {
        _uiState.update { it.copy(uploadStatus = UploadStatus.Idle) }
    }

    fun createPhotoUri(): MediaUri? = mediaCaptureRepository.createPhotoUri()

    fun createVideoUri(): MediaUri? = mediaCaptureRepository.createVideoUri()

    fun cancelPhotoCapture(uri: MediaUri) {
        viewModelScope.launch {
            mediaUploadRepository.uploadMediaToCache(listOf(uri), deleteAfterUpload = true)
        }
        _uiState.update { it.copy(photoUri = null) }
    }

    fun cancelVideoCapture(uri: MediaUri) {
        viewModelScope.launch {
            mediaUploadRepository.uploadMediaToCache(listOf(uri), deleteAfterUpload = true)
        }
        _uiState.update { it.copy(videoUri = null) }
    }

    fun updateNoteText(text: String) {
        analyticsRepository.logNoteCreate(text)
        _uiState.update {
            it.copy(noteText = text, isNoteTextTooLong = text.length > MAX_NOTE_TEXT_LENGTH)
        }
    }

    fun addMedia(uriList: List<MediaUri>) {
        viewModelScope.launch { mediaUploadRepository.uploadMediaToCache(uriList) }

        viewModelScope.launch {
            mediaUploadRepository.buildLocalMediaDetails(uriList)
                .onSuccess { newMediaDetailList ->
                    // Combined inside `update` rather than from a `_uiState.value` read taken
                    // before it: this runs after a suspension, and each call to this function
                    // launches its own coroutine, so two picks landing together would otherwise
                    // both start from the same base list and the second would drop the first's
                    // additions.
                    _uiState.update { state ->
                        val combinedMediaList = newMediaDetailList + state.mediaList
                        if (combinedMediaList.size > MAX_NOTE_MEDIA_ITEMS) {
                            state.copy(
                                mediaList = combinedMediaList.take(MAX_NOTE_MEDIA_ITEMS),
                                uploadStatus = UploadStatus.MediaLimitReached
                            )
                        } else {
                            state.copy(mediaList = combinedMediaList)
                        }
                    }
                }
                .onFailure(errorHandler::logError)
        }
    }

    fun removeMediaAt(index: Int) {
        _uiState.update { state ->
            if (index in state.mediaList.indices) {
                state.copy(mediaList = state.mediaList.filterIndexed { i, _ -> i != index })
            } else {
                state
            }
        }
    }

    fun updatePhotoUri(uri: MediaUri?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun updateVideoUri(uri: MediaUri?) {
        _uiState.update { it.copy(videoUri = uri) }
    }
}