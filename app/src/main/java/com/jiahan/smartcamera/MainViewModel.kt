package com.jiahan.smartcamera

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.auth.AuthRoute
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AppUpdateRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.domain.AppUpdateState
import com.jiahan.smartcamera.home.HomeRoute
import com.jiahan.smartcamera.note.IncomingShare
import com.jiahan.smartcamera.note.IncomingShareHandler
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

data class MainUiState(
    val isAppReady: Boolean = false,
    val startDestination: Any = AuthRoute,
    val showBottomBar: Boolean = true,
    val scrollToTop: Long? = null,
    val pendingNoteId: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val errorHandler: ErrorHandler,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val analyticsRepository: AnalyticsRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val incomingShareHandler: IncomingShareHandler,
    private val appUpdateRepository: AppUpdateRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    // requestUpdateFlow() checks availability once per subscription, then keeps emitting as the
    // flexible update progresses (Available -> InProgress -> Downloaded), so a single collector
    // living as long as MainActivity is enough to drive both the initial prompt and the
    // "ready to restart" state.
    val updateState = appUpdateRepository.observeUpdateState()
        .catch { e -> errorHandler.logError(e) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = AppUpdateState.NotAvailable
        )

    val isDarkTheme = userPreferencesRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = false
        )

    val hasPendingShare = incomingShareHandler.incomingShare
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            remoteConfigRepository.fetchAndActivateConfig()
                .onFailure { e -> errorHandler.logError(e) }
            val destination =
                if (authRepository.currentUserId != null && authRepository.isCurrentUserEmailVerified)
                    HomeRoute
                else
                    AuthRoute
            _uiState.update { it.copy(startDestination = destination, isAppReady = true) }
            if (destination == HomeRoute) {
                analyticsRepository.setUserId(authRepository.currentUserId)
                userRepository.registerForPushNotifications()
                    .onFailure { e -> errorHandler.logError(e) }
                val today = clock.todayIn(TimeZone.currentSystemDefault())
                userRepository.recordUserActivity(today)
                    .onFailure { e -> errorHandler.logError(e) }
            }
        }
    }

    fun updateBottomBarVisibility(showBottomBar: Boolean) {
        _uiState.update { it.copy(showBottomBar = showBottomBar) }
    }

    fun updateStartDestination(destination: Any) {
        _uiState.update { it.copy(startDestination = destination) }
    }

    fun triggerScrollToTop() {
        _uiState.update { it.copy(scrollToTop = System.currentTimeMillis()) }
    }

    fun consumeScrollToTopEvent() {
        _uiState.update { it.copy(scrollToTop = null) }
    }

    fun handleIncomingIntent(intent: Intent) {
        val share = when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val uri =
                    IntentCompat.getParcelableExtra(
                        intent,
                        Intent.EXTRA_STREAM,
                        Uri::class.java
                    )
                if (text != null || uri != null) IncomingShare(text, listOfNotNull(uri)) else null
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                if (!uris.isNullOrEmpty()) IncomingShare(text = null, uris = uris) else null
            }

            else -> null
        }
        share?.let(incomingShareHandler::postShare)
    }

    fun onNotificationNoteIdReceived(noteId: String) {
        _uiState.update { it.copy(pendingNoteId = noteId) }
    }

    fun consumePendingNoteId() {
        _uiState.update { it.copy(pendingNoteId = null) }
    }

    /**
     * Forwards the Activity's launcher to the data layer, which owns the Play handle needed to
     * start the flow. Nothing about the launcher is retained here.
     */
    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        appUpdateRepository.startFlexibleUpdate(launcher)
    }

    fun completeUpdate() {
        viewModelScope.launch {
            appUpdateRepository.completeUpdate()
                .onFailure { e -> errorHandler.logError(e) }
        }
    }
}