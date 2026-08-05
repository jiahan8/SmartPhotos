package com.jiahan.smartcamera.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class FavoriteUiState(
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val noteToDelete: HomeNote? = null
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteActions: NoteActionsDelegate,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoriteUiState())
    val uiState = _uiState.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = merge(noteActions.actionError, _syncError)

    private val searchQuery = _uiState
        .map { it.searchQuery }
        .distinctUntilChanged()

    val notes = searchQuery
        .debounce(DEBOUNCE_MS.milliseconds)
        .flatMapLatest { query -> noteRepository.getFavoriteNotesStream(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = emptyList(),
        )

    val isLoading = combine(_isSyncing, notes) { syncing, notesList ->
        syncing && notesList.isEmpty()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
        initialValue = true,
    )

    init {
        viewModelScope.launch { syncNotes() }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            syncNotes()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun syncNotes() {
        _isSyncing.value = true
        noteRepository.syncFavoriteNotes()
            .onSuccess { analyticsRepository.logFavoriteSearchCustomEvent(_uiState.value.searchQuery) }
            .onFailure { e ->
                errorHandler.logError(e)
                _syncError.tryEmit(errorHandler.getErrorMessage(e))
            }
        _isSyncing.value = false
    }

    fun deleteNote(documentPath: String) {
        viewModelScope.launch { noteActions.deleteNote(documentPath) }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch { noteActions.favoriteNote(homeNote) }
    }

    fun setNoteToDelete(note: HomeNote?) {
        _uiState.update { it.copy(noteToDelete = note) }
    }
}