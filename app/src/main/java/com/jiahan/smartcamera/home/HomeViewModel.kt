package com.jiahan.smartcamera.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeContent {
    data object Loading : HomeContent
    data class Success(val notes: List<HomeNote>) : HomeContent
    data class Error(val message: String) : HomeContent
}

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val noteToDelete: HomeNote? = null,
    val isExploreIconVisible: Boolean = false
)

/**
 * How far the remote fetch has got. It only ever breaks a tie: once the mirror has rows, what the
 * fetch is doing stops being the user's problem and the feed just renders them.
 */
private sealed interface FetchStatus {
    data object Pending : FetchStatus
    data object Settled : FetchStatus
    data class Failed(val message: String) : FetchStatus
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val noteErrorReporter: NoteErrorReporter,
    private val noteShare: NoteShareDelegate,
    private val errorHandler: ErrorHandler,
    private val remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _fetchError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = merge(noteErrorReporter.actionError, _fetchError)
    val shareEvent = noteShare.shareEvent

    private val fetchStatus = MutableStateFlow<FetchStatus>(FetchStatus.Pending)

    private val pageSize = DEFAULT_PAGE_SIZE

    /**
     * How much of the mirror the feed is showing, widened one page at a time as [loadMoreNotes]
     * fetches.
     *
     * Without it the feed would render the whole table, which is not the same list: `searchNotes`
     * and `getNote` write into `notes` too, so notes the user never paged to would appear in Home
     * because they once searched for them. The window keeps what is rendered equal to what was
     * fetched, which is what pagination is for.
     */
    private val notesLimit = MutableStateFlow(pageSize)

    /**
     * The feed, read from the Room mirror rather than from the fetch that fills it. [fetchNotes]
     * still walks Firestore for the pages, but it hands nothing to the UI -- `getNotes` writes each
     * page into the table on its way through and this re-emits, which is also why a delete or a
     * favorite needs no local patch to the list any more.
     *
     * [FetchStatus] decides only what an *empty* mirror means, and [FetchStatus.Pending] is the
     * branch that matters: Room answers long before the first fetch does, so without it a fresh
     * install would greet a user with "create your first note" while their notes were still in
     * flight. A failed fetch is the mirror image -- an error screen is right when there is nothing
     * cached, and wrong when there is, so with rows on screen the failure goes to [actionError]
     * instead and the cached feed stays up.
     */
    val content: StateFlow<HomeContent> =
        combine(
            notesLimit.flatMapLatest { noteRepository.getNotesStream(it) },
            fetchStatus
        ) { notes, status ->
            when {
                notes.isNotEmpty() -> HomeContent.Success(notes)
                status is FetchStatus.Failed -> HomeContent.Error(status.message)
                status is FetchStatus.Settled -> HomeContent.Success(emptyList())
                else -> HomeContent.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = HomeContent.Loading
        )

    private var nextCursor: NoteCursor? = null
    private var hasMoreData = true
    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        reload(showRefreshIndicator = false)
        // The one NoteHandler event the mirror does not make redundant. Deletions, favorites and
        // edits all write through to Room, so `content` re-emits on its own -- but `addNote`
        // delegates to the createNote Cloud Function and drops its result, and the new note's id
        // and server-stamped `created` exist only server-side, so nothing can mirror it locally.
        // Refetching the first page is still the only way it reaches the table.
        viewModelScope.launch {
            noteHandler.noteAddedEvent.collect { reload(showRefreshIndicator = false) }
        }
        viewModelScope.launch {
            remoteConfigRepository.observeExploreIconVisible().collect { visible ->
                _uiState.update { it.copy(isExploreIconVisible = visible) }
            }
        }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    fun refresh() {
        reload(showRefreshIndicator = true)
    }

    /**
     * Refetches from the first page -- the one path that resets [nextCursor], so every caller that
     * wants the feed rebuilt goes through it.
     *
     * A page load still in flight is cancelled first. That guard survives the move onto Room for a
     * different reason than it was written for: the mirror is keyed by note id, so a late page can
     * no longer splice a stale window into the list. What it would still do is advance [nextCursor]
     * past a page nobody kept, leaving a hole in the feed until the next reload.
     */
    private fun reload(showRefreshIndicator: Boolean) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            loadMoreJob?.cancelAndJoin()
            _uiState.update {
                it.copy(isRefreshing = showRefreshIndicator, isLoadingMore = false)
            }
            fetchNotes(initialLoading = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchNotes(initialLoading: Boolean) {
        if (initialLoading) {
            nextCursor = null
            hasMoreData = true
            notesLimit.value = pageSize
        }
        if (!hasMoreData) return

        noteRepository.getNotes(cursor = nextCursor, pageSize = pageSize)
            .onSuccess { notePage ->
                // The page itself is not used: it has already been mirrored into Room, and
                // `content` is collecting that. Only the pagination position is ours to keep.
                nextCursor = notePage.nextCursor
                hasMoreData = notePage.hasMore
                if (!initialLoading) notesLimit.update { it + pageSize }
                fetchStatus.value = FetchStatus.Settled
            }
            .onFailure { e ->
                errorHandler.logError(e)
                // A page that failed to append leaves the feed intact and the cursor where it was,
                // so there is nothing to tell the user about -- same as before Room.
                if (!initialLoading) return@onFailure

                val message = errorHandler.getErrorMessage(e)
                // Only an empty cache earns the full-screen error. With rows to show, blanking
                // them would throw away readable notes over a failed refresh, so the feed stays
                // and the failure surfaces transiently instead of vanishing.
                if (noteRepository.getNotesStream(notesLimit.value).first().isEmpty()) {
                    fetchStatus.value = FetchStatus.Failed(message)
                } else {
                    _fetchError.tryEmit(message)
                }
            }
    }

    fun loadMoreNotes() {
        if (reloadJob?.isActive == true) return
        if (_uiState.value.isLoadingMore || !hasMoreData) return

        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetchNotes(initialLoading = false)
            _uiState.update { it.copy(isLoadingMore = false) }
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