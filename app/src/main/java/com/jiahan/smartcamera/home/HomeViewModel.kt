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
            // Create a ByteArray from the InputStream instead of using the stream directly
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw IllegalStateException("Failed to open image stream")
            val bytes = inputStream.use { it.readBytes() }
            folderRef.putBytes(bytes).await()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _uploading.value = false
        }
    }
}