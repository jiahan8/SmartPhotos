package com.jiahan.smartcamera.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.domain.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()
    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()
    private val _fullName = MutableStateFlow("")
    val fullName = _fullName.asStateFlow()
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()
    private val _profilePictureUrl = MutableStateFlow<String?>(null)
    val profilePictureUrl = _profilePictureUrl.asStateFlow()

    init {
        viewModelScope.launch {
            _user.value = profileRepository.getUser()
            _email.value = _user.value?.email ?: ""
            _fullName.value = _user.value?.fullName ?: ""
            _username.value = _user.value?.username ?: ""
            _profilePictureUrl.value = _user.value?.profilePicture
        }
    }

    fun updateEmailText(text: String) {
        _email.value = text
    }

    fun updateFullNameText(text: String) {
        _fullName.value = text
    }

    fun updateUsernameText(text: String) {
        _username.value = text
    }

    fun updateProfilePictureUrl(url: String) {
        _profilePictureUrl.value = url
    }

    fun updateUserProfile() {
        viewModelScope.launch {
            profileRepository.updateUserProfile("", null)
        }
    }
}