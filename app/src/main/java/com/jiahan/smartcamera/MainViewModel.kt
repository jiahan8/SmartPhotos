package com.jiahan.smartcamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.util.AppConstants.STATE_FLOW_TIMEOUT_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val errorHandler: ErrorHandler,
    private val authRepository: AuthRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _isAppReady = MutableStateFlow(false)
    val isAppReady = _isAppReady.asStateFlow()
    private val _startDestination = MutableStateFlow(Screen.Auth.route)
    val startDestination = _startDestination.asStateFlow()
    private val _showBottomBar = MutableStateFlow(true)
    val showBottomBar = _showBottomBar.asStateFlow()
    private val _scrollToTop = MutableStateFlow<Long?>(null)
    val scrollToTop = _scrollToTop.asStateFlow()

    val isDarkTheme = userPreferencesRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            remoteConfigRepository.fetchAndActivateConfig()
                .onFailure { e -> errorHandler.logError(e) }
            _startDestination.value =
                if (authRepository.currentUserId != null && authRepository.isCurrentUserEmailVerified)
                    Screen.Home.route
                else
                    Screen.Auth.route
            _isAppReady.value = true
        }
    }

    fun updateBottomBarVisibility(showBottomBar: Boolean) {
        _showBottomBar.value = showBottomBar
    }

    fun updateStartDestination(destination: String) {
        _startDestination.value = destination
    }

    fun triggerScrollToTop() {
        _scrollToTop.value = System.currentTimeMillis()
    }

    fun consumeScrollToTopEvent() {
        _scrollToTop.value = null
    }
}