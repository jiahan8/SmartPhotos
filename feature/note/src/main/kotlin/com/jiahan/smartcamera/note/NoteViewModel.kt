package com.jiahan.smartcamera.note

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_MEDIA_ITEMS
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_TEXT_LENGTH
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.toMediaUri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jiahan.smartcamera.core.common.R as CommonR

sealed interface UploadStatus {
    data object Idle : UploadStatus
    data object Uploading : UploadStatus
    data object Success : UploadStatus
    data class Error(val message: String) : UploadStatus
}

data class NoteUiState(
    val noteText: String = "",
    val noteTextError: String? = null,
    val photoUri: Uri? = null,
    val videoUri: Uri? = null,
    val mediaList: List<NoteMediaDetail> = emptyList(),
    val uploadStatus: UploadStatus = UploadStatus.Idle
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val mediaUploadRepository: MediaUploadRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val mediaFileRepository: MediaFileRepository,
    incomingShareHandler: IncomingShareHandler,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        incomingShareHandler.consume()?.let { share ->
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
                    state.noteTextError == null
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
    // getErrorMessage renders those, so this is the same message with the Firebase type left in
    // the data layer where it belongs.
    private fun handleUploadFailure(e: Throwable) {
        errorHandler.logError(e)
        _uiState.update {
            it.copy(uploadStatus = UploadStatus.Error(errorHandler.getErrorMessage(e)))
        }
    }

    fun resetUploadStatus() {
        _uiState.update { it.copy(uploadStatus = UploadStatus.Idle) }
    }

    fun createPhotoUri(): Uri? = mediaFileRepository.createPhotoUri()

    fun createVideoUri(): Uri? = mediaFileRepository.createVideoUri()

    fun cancelPhotoCapture(uri: Uri) {
        viewModelScope.launch {
            mediaUploadRepository.uploadMediaToCache(
                listOf(uri.toMediaUri()),
                deleteAfterUpload = true
            )
        }
        _uiState.update { it.copy(photoUri = null) }
    }

    fun cancelVideoCapture(uri: Uri) {
        viewModelScope.launch {
            mediaUploadRepository.uploadMediaToCache(
                listOf(uri.toMediaUri()),
                deleteAfterUpload = true
            )
        }
        _uiState.update { it.copy(videoUri = null) }
    }

    fun updateNoteText(text: String) {
        analyticsRepository.logNoteCreate(text)
        _uiState.update {
            it.copy(
                noteText = text,
                noteTextError = when {
                    text.length > MAX_NOTE_TEXT_LENGTH ->
                        resourceProvider.getString(CommonR.string.note_validation)

                    else -> null
                }
            )
        }
    }

    fun addMedia(uriList: List<Uri>) {
        val mediaUriList = uriList.map { it.toMediaUri() }
        viewModelScope.launch { mediaUploadRepository.uploadMediaToCache(mediaUriList) }

        viewModelScope.launch {
            mediaUploadRepository.buildLocalMediaDetails(mediaUriList)
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
                                uploadStatus = UploadStatus.Error(
                                    resourceProvider.getString(CommonR.string.note_media_limit)
                                )
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

    fun updatePhotoUri(uri: Uri?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun updateVideoUri(uri: Uri?) {
        _uiState.update { it.copy(videoUri = uri) }
    }
}