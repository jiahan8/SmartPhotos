package com.jiahan.smartcamera.note

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()
    private val _uploadSuccess = MutableStateFlow(false)
    val uploadSuccess = _uploadSuccess.asStateFlow()
    private val _uploadError = MutableStateFlow(false)
    val uploadError = _uploadError.asStateFlow()
    private val _postTextError = MutableStateFlow<String?>(null)
    val postTextError = _postTextError.asStateFlow()

    private val _postText = MutableStateFlow("")
    val postText = _postText.asStateFlow()
    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri = _photoUri.asStateFlow()
    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri = _videoUri.asStateFlow()
    private val _uriList = MutableStateFlow<List<Uri>>(emptyList())
    val uriList = _uriList.asStateFlow()
    private val _postButtonEnabled = MutableStateFlow(false)
    val postButtonEnabled = _postButtonEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            remoteConfigRepository.fetchAndActivateConfig()
            combine(
                _uploading,
                _postText,
                _uriList,
                _postTextError
            ) { uploading, postText, uriList, postTextError ->
                !uploading && (postText.isNotBlank() || uriList.isNotEmpty()) && postTextError == null
            }.collect {
                _postButtonEnabled.value = it
            }
        }
    }

    fun uploadImageToFirebase(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            try {
                remoteConfigRepository.fetchAndActivateConfig()
                val storage =
                    Firebase.storage(remoteConfigRepository.getStorageUrl())
                val folderRef =
                    storage.reference.child("${remoteConfigRepository.getStorageFolderName()}/${UUID.randomUUID()}.jpg")
                _uploading.value = true

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

    fun uploadPost(text: String, uriList: List<Uri>) {
        viewModelScope.launch {
            try {
                _uploading.value = true
                val mediaUrlList = uploadMediaToFirebase(uriList)

                noteRepository.saveNote(
                    HomeNote(
                        text = text,
                        mediaUrlList = mediaUrlList
                    )
                )
                _uploadSuccess.value = true
                noteHandler.notifyNoteAdded()
            } catch (e: Exception) {
                e.printStackTrace()
                _uploading.value = false
                _uploadSuccess.value = false
                _uploadError.value = true
            }
        }
    }

    private suspend fun uploadMediaToFirebase(uriList: List<Uri>): List<String> {
        return coroutineScope {
            uriList.map { uri ->
                async(Dispatchers.IO) {
                    try {
                        val storage = Firebase.storage(remoteConfigRepository.getStorageUrl())
                        val fileName = "${UUID.randomUUID()}_${uri.lastPathSegment}"
                        val extension = if (uri.toString().endsWith(".mp4")) ".mp4" else ".jpg"
                        val folderRef =
                            storage.reference.child("${remoteConfigRepository.getStorageFolderName()}/$fileName$extension")

                        folderRef.putFile(uri).await()
                        folderRef.downloadUrl.await().toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    fun resetUploadSuccess() {
        _uploadSuccess.value = false
    }

    fun resetUploadError() {
        _uploadError.value = false
    }

    fun resetUploading() {
        _uploading.value = false
    }

    fun createImageUri(context: Context): Uri? {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val imageFile = File.createTempFile(
            "smartcamera_${timeStamp}_",
            ".jpg",
            storageDir
        )

        return getUriForFile(
            context,
            "com.jiahan.smartcamera.fileprovider",
            imageFile
        )
    }

    fun createVideoUri(context: Context): Uri? {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val imageFile = File.createTempFile(
            "smartcamera_${timeStamp}_",
            ".mp4",
            storageDir
        )

        return getUriForFile(
            context,
            "com.jiahan.smartcamera.fileprovider",
            imageFile
        )
    }

    fun updatePostText(text: String) {
        _postText.value = text
        validatePostText(text)
    }

    fun updateUriList(uriList: List<Uri>) {
        _uriList.value = uriList + _uriList.value
    }

    fun removeUriFromList(uri: Uri) {
        _uriList.value = _uriList.value.filter { it != uri }
    }

    fun updatePhotoUri(uri: Uri?) {
        _photoUri.value = uri
    }

    fun updateVideoUri(uri: Uri?) {
        _videoUri.value = uri
    }

    private fun validatePostText(text: String) {
        _postTextError.value = when {
            text.length > 200 -> resourceProvider.getString(R.string.post_validation)
            else -> null
        }
    }
}