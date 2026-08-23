package com.jiahan.smartcamera.note

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_MEDIA_ITEMS
import com.jiahan.smartcamera.util.AppConstants.MAX_POST_TEXT_LENGTH
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.noteErrorMessageResId
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

sealed interface UploadStatus {
    data object Idle : UploadStatus
    data object Uploading : UploadStatus
    data object Success : UploadStatus
    data class Error(val message: String) : UploadStatus
}

data class NoteUiState(
    val postText: String = "",
    val postTextError: String? = null,
    val photoUri: Uri? = null,
    val videoUri: Uri? = null,
    val mediaList: List<NoteMediaDetail> = emptyList(),
    val uploadStatus: UploadStatus = UploadStatus.Idle
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val mediaFileRepository: MediaFileRepository,
    private val noteHandler: NoteHandler,
    incomingShareHandler: IncomingShareHandler,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        incomingShareHandler.consume()?.let { share ->
            if (!share.text.isNullOrBlank()) updatePostText(share.text)
            if (share.uris.isNotEmpty()) updateUriList(share.uris)
        }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    val postButtonEnabled = _uiState
        .map { state ->
            state.uploadStatus !is UploadStatus.Uploading &&
                    (state.postText.isNotBlank() || state.mediaList.isNotEmpty()) &&
                    state.postTextError == null
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val userPreferences = userPreferencesRepository.userPreferencesFlow
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = UserPreferences(
                isDarkTheme = false,
                username = "",
                profilePicture = null
            )
        )

    val username = userPreferences
        .map { it.username }
        .distinctUntilChanged()

    val profilePicture = userPreferences
        .map { it.profilePicture }
        .distinctUntilChanged()

    fun uploadPost() {
        val text = _uiState.value.postText.trim().ifBlank { null }
        val media = _uiState.value.mediaList
        viewModelScope.launch {
            _uiState.update { it.copy(uploadStatus = UploadStatus.Uploading) }
            noteRepository.uploadMediaToFirebase(media)
                .onSuccess { mediaDetailList ->
                    noteRepository.addNote(
                        HomeNote(
                            noteId = "",
                            text = text,
                            mediaList = mediaDetailList,
                            username = ""
                        )
                    )
                        .onSuccess {
                            noteHandler.notifyNoteAdded()
                            _uiState.update { it.copy(uploadStatus = UploadStatus.Success) }
                        }
                        .onFailure { e -> handleUploadFailure(e) }
                }
                .onFailure { e -> handleUploadFailure(e) }
        }
    }

    // createNote's validation errors all share the invalid-argument code, so
    // noteErrorMessageResId() reads the structured `details.reason` payload
    // to map them to a specific, localized message instead of leaking the
    // Cloud Function's raw English error text.
    private fun handleUploadFailure(e: Throwable) {
        errorHandler.logError(e)
        val message = noteErrorMessageResId(e)?.let(resourceProvider::getString)
            ?: errorHandler.getErrorMessage(e)
        _uiState.update { it.copy(uploadStatus = UploadStatus.Error(message)) }
    }

    fun resetUploadState() {
        _uiState.update { it.copy(uploadStatus = UploadStatus.Idle) }
    }

    fun createImageUri(): Uri? = mediaFileRepository.createImageUri()

    fun createVideoUri(): Uri? = mediaFileRepository.createVideoUri()

    fun cancelPhotoCapture(uri: Uri) {
        viewModelScope.launch {
            noteRepository.quickUploadMediaToFirebase(listOf(uri), deleteAfterUpload = true)
        }
        _uiState.update { it.copy(photoUri = null) }
    }

    fun cancelVideoCapture(uri: Uri) {
        viewModelScope.launch {
            noteRepository.quickUploadMediaToFirebase(listOf(uri), deleteAfterUpload = true)
        }
        _uiState.update { it.copy(videoUri = null) }
    }

    fun updatePostText(text: String) {
        analyticsRepository.logNoteCustomEvent(text)
        _uiState.update {
            it.copy(
                postText = text,
                postTextError = when {
                    text.length > MAX_POST_TEXT_LENGTH -> resourceProvider.getString(R.string.note_validation)
                    else -> null
                }
            )
        }
    }

    fun updateUriList(uriList: List<Uri>) {
        viewModelScope.launch { noteRepository.quickUploadMediaToFirebase(uriList) }

        viewModelScope.launch {
            noteRepository.buildLocalMediaDetails(uriList)
                .onSuccess { newMediaDetailList ->
                    val combinedMediaList = newMediaDetailList + _uiState.value.mediaList
                    if (combinedMediaList.size > MAX_NOTE_MEDIA_ITEMS) {
                        _uiState.update {
                            it.copy(
                                mediaList = combinedMediaList.take(MAX_NOTE_MEDIA_ITEMS),
                                uploadStatus = UploadStatus.Error(
                                    resourceProvider.getString(R.string.note_media_limit)
                                )
                            )
                        }
                    } else {
                        _uiState.update { it.copy(mediaList = combinedMediaList) }
                    }
                }
                .onFailure { e -> errorHandler.logError(e) }
        }
    }

    fun removeUriFromList(index: Int) {
        val currentList = _uiState.value.mediaList
        if (index >= 0 && index < currentList.size) {
            val newList = currentList.toMutableList()
            newList.removeAt(index)
            _uiState.update { it.copy(mediaList = newList) }
        }
    }

    fun updatePhotoUri(uri: Uri?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun updateVideoUri(uri: Uri?) {
        _uiState.update { it.copy(videoUri = uri) }
    }
}