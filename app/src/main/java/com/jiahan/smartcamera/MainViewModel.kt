package com.jiahan.smartcamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isAppReady: Boolean = false,
    val startDestination: String = Screen.Auth.route,
    val showBottomBar: Boolean = true,
    val scrollToTop: Long? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val errorHandler: ErrorHandler,
    private val authRepository: AuthRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    val isDarkTheme = userPreferencesRepository.userPreferencesFlow
        .map { it.isDarkTheme }
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
                    Screen.Home.route
                else
                    Screen.Auth.route
            _uiState.update { it.copy(startDestination = destination, isAppReady = true) }
        }
    }

    fun updateBottomBarVisibility(showBottomBar: Boolean) {
        _uiState.update { it.copy(showBottomBar = showBottomBar) }
    }

    fun updateStartDestination(destination: String) {
        _uiState.update { it.copy(startDestination = destination) }
    }

    fun triggerScrollToTop() {
        _uiState.update { it.copy(scrollToTop = System.currentTimeMillis()) }
    }

    fun consumeScrollToTopEvent() {
        _uiState.update { it.copy(scrollToTop = null) }
    }
}