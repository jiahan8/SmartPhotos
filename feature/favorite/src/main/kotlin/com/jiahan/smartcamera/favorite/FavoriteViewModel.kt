package com.jiahan.smartcamera.favorite

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed interface FavoriteContent {
    data object Loading : FavoriteContent
    data class Success(val notes: List<HomeNote>) : FavoriteContent
    data class Error(val message: String) : FavoriteContent
}

data class FavoriteUiState(
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val noteToDelete: HomeNote? = null
)

/**
 * How far the favorites sync has got. As on Home, it only ever breaks a tie: with rows in the
 * mirror, what the sync is doing stops being the user's problem and the list just renders them.
 */
private sealed interface SyncStatus {
    data object Pending : SyncStatus
    data object Settled : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteErrorReporter: NoteErrorReporter,
    private val noteShare: NoteShareDelegate,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoriteUiState())
    val uiState = _uiState.asStateFlow()
    private val _syncError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = merge(noteErrorReporter.actionError, _syncError)
    val shareEvent = noteShare.shareEvent

    private val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Pending)

    /**
     * One debounce timer, shared by the mirror read below and the analytics collector in `init`.
     *
     * Both used to collect a cold `searchQuery.debounce(...)` of their own, which meant two timers
     * *and* two `map`/`distinctUntilChanged` chains over [_uiState] for one user-visible query.
     * Nothing here raced the way Search's two halves could -- analytics feeds no state -- so this
     * is duplicated work rather than a bug, and it is shared for the same reason Search's is.
     *
     * `replay = 1` matters more than it looks: [content] is `WhileSubscribed`, so without it,
     * returning to the screen after the timeout re-ran the debounce and left the list empty for
     * another 300 ms.
     */
    private val debouncedQuery = _uiState
        .map { it.searchQuery }
        .distinctUntilChanged()
        .debounce(DEBOUNCE_MS.milliseconds)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val notesStream = debouncedQuery
        .flatMapLatest { query -> noteRepository.getFavoriteNotesStream(query) }

    /**
     * Favorites read from the mirror, with [SyncStatus] breaking the tie only when that mirror is
     * empty -- the same three-way shape Home, Search and NotePreview use.
     *
     * This used to combine against a plain `isSyncing` boolean, and a boolean has no way to say
     * *failed*: a cold start with no network left an empty mirror rendering "favorite a note to see
     * it here", which tells the user they have no favorites when the truth is the sync never
     * landed. Pending and settled were the two states it could express; the third is the one that
     * mattered.
     */
    val content: StateFlow<FavoriteContent> =
        combine(notesStream, syncStatus) { notes, status ->
            when {
                notes.isNotEmpty() -> FavoriteContent.Success(notes)
                status is SyncStatus.Failed -> FavoriteContent.Error(status.message)
                status is SyncStatus.Settled -> FavoriteContent.Success(emptyList())
                else -> FavoriteContent.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = FavoriteContent.Loading,
        )

    init {
        viewModelScope.launch { syncNotes() }
        viewModelScope.launch {
            debouncedQuery.collect { query ->
                if (query.isNotBlank()) {
                    analyticsRepository.logFavoriteSearchCustomEvent(query)
                }
            }
        }
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
        noteRepository.syncFavoriteNotes()
            .onSuccess { syncStatus.value = SyncStatus.Settled }
            .onFailure { e ->
                errorHandler.logError(e)
                val message = errorHandler.getErrorMessage(e)
                // Only an empty list earns the full-screen error. With favorites already cached,
                // `content` keeps rendering them -- `notes.isNotEmpty()` wins in the combine above,
                // so a Failed status would never be seen -- and the failure surfaces transiently
                // instead of vanishing. Same split as `HomeViewModel.fetchNotes`, and the mirror is
                // re-read here rather than inferred, because a first sync and a refresh can each
                // land on either side of it.
                if (noteRepository.getFavoriteNotesStream(_uiState.value.searchQuery)
                        .first()
                        .isEmpty()
                ) {
                    syncStatus.value = SyncStatus.Failed(message)
                } else {
                    _syncError.tryEmit(message)
                }
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