package com.jiahan.smartcamera.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed interface SearchContent {
    data object Idle : SearchContent
    data object Loading : SearchContent
    data class Success(val notes: List<HomeNote>) : SearchContent
    data class Error(val message: String) : SearchContent
}

data class SearchUiState(
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val noteToDelete: HomeNote? = null
)

/**
 * How far the remote search has got, for the current query. As on Home, it only decides what an
 * empty result set means -- with matches to show, the query's state stops mattering.
 */
private sealed interface SearchStatus {
    data object Idle : SearchStatus
    data object Searching : SearchStatus
    data object Settled : SearchStatus
    data class Failed(val message: String) : SearchStatus
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteErrorReporter: NoteErrorReporter,
    private val noteShare: NoteShareDelegate,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()
    val actionError = noteErrorReporter.actionError
    val shareEvent = noteShare.shareEvent

    val searchQuery = _uiState
        .map { it.searchQuery }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS), "")

    private val debouncedQuery = searchQuery.debounce(DEBOUNCE_MS.milliseconds)

    private val searchStatus = MutableStateFlow<SearchStatus>(SearchStatus.Idle)

    /**
     * Results read from the mirror rather than from the fetch that fills it.
     *
     * `searchNotes` is still what reaches Firestore -- it reads the whole collection and writes its
     * results through -- so this is not a narrower search than before, just a live one: a note
     * deleted, favorited or edited on another screen updates here with no `NoteHandler` event to
     * collect, and none of the three list transforms this ViewModel used to apply by hand.
     */
    private val results: Flow<List<HomeNote>> = debouncedQuery.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList()) else noteRepository.searchNotesStream(query)
    }

    val content: StateFlow<SearchContent> =
        combine(results, searchStatus) { notes, status ->
            when {
                status is SearchStatus.Idle -> SearchContent.Idle
                notes.isNotEmpty() -> SearchContent.Success(notes)
                status is SearchStatus.Failed -> SearchContent.Error(status.message)
                status is SearchStatus.Settled -> SearchContent.Success(emptyList())
                else -> SearchContent.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = SearchContent.Idle
        )

    init {
        viewModelScope.launch {
            debouncedQuery.collect { query ->
                if (query.isBlank()) {
                    searchStatus.value = SearchStatus.Idle
                } else {
                    searchNotes(query)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        analyticsRepository.logSearchCustomEvent(query)
        analyticsRepository.logSearchEvent(query)
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val query = _uiState.value.searchQuery
            if (query.isNotBlank()) searchNotes(query)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun searchNotes(query: String) {
        searchStatus.value = SearchStatus.Searching
        noteRepository.searchNotes(query = query)
            .onSuccess { searchStatus.value = SearchStatus.Settled }
            .onFailure { e ->
                errorHandler.logError(e)
                searchStatus.value = SearchStatus.Failed(errorHandler.getErrorMessage(e))
            }
    }

    fun deleteNote(noteId: String) {
        // The row leaves the `notes` table, so every screen observing it drops the note with no
        // list transform here. Was NoteActionsDelegate, which inlined to this when the Room mirror
        // left it holding one repository call and one error report.
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
                .onFailure { e -> noteErrorReporter.reportError(e) }
        }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch {
            noteRepository.favoriteNote(homeNote)
                .onFailure { e -> noteErrorReporter.reportError(e) }
        }
    }

    fun setNoteToDelete(note: HomeNote?) {
        _uiState.update { it.copy(noteToDelete = note) }
    }

    fun shareNote(note: HomeNote) {
        viewModelScope.launch { noteShare.shareNote(note) }
    }
}