package com.jiahan.smartcamera.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.jiahan.smartcamera.repository.RemoteConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
) : ViewModel() {

    private val _uploading = mutableStateOf(false)

    suspend fun uploadImageToFirebase(context: Context, imageUri: Uri) {
        remoteConfigRepository.fetchAndActivateConfig()
        val storage =
            Firebase.storage(remoteConfigRepository.getStorageUrl())
        val folderRef =
            storage.reference.child("${remoteConfigRepository.getStorageFolderName()}/${UUID.randomUUID()}.jpg")
        _uploading.value = true
        try {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                folderRef.putStream(stream).await()
                _uploading.value = true
            } ?: error("Failed to open image stream")
        } catch (e: Exception) {
            e
        } finally {
            _uploading.value = false
        }
    }
}