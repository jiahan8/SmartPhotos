package com.jiahan.smartcamera.note

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.SearchRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.Util.createVideoThumbnail
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val noteRepository: NoteRepository,
    private val searchRepository: SearchRepository,
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
    private val _postButtonEnabled = MutableStateFlow(false)
    val postButtonEnabled = _postButtonEnabled.asStateFlow()

    private val _postText = MutableStateFlow("")
    val postText = _postText.asStateFlow()
    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri = _photoUri.asStateFlow()
    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri = _videoUri.asStateFlow()
    private val _mediaList = MutableStateFlow<List<NoteMediaDetail>>(emptyList())
    val mediaList = _mediaList.asStateFlow()
    private val _videoThumbnails = mutableStateMapOf<Uri, Bitmap?>()

    init {
        viewModelScope.launch {
            remoteConfigRepository.fetchAndActivateConfig()
            combine(
                _uploading,
                _postText,
                _mediaList,
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

    fun uploadPost(text: String, mediaList: List<NoteMediaDetail>) {
        viewModelScope.launch {
            try {
                _uploading.value = true
                val mediaDetailList = uploadMediaToFirebase(mediaList)

                noteRepository.saveNote(
                    HomeNote(
                        text = text,
                        mediaList = mediaDetailList
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

    private suspend fun uploadMediaToFirebase(noteMediaDetailList: List<NoteMediaDetail>): List<MediaDetail> {
        return coroutineScope {
            noteMediaDetailList.map { noteMediaDetail ->
                async(Dispatchers.IO) {
                    try {
                        val storage = Firebase.storage(remoteConfigRepository.getStorageUrl())
                        val folderName = remoteConfigRepository.getStorageFolderName()

                        val mediaId = UUID.randomUUID().toString()
                        val extension = if (noteMediaDetail.isVideo) ".mp4" else ".jpg"
                        val storageRef = storage.reference.child("$folderName/$mediaId$extension")

                        val mediaUri = noteMediaDetail.photoUri ?: noteMediaDetail.videoUri
                        if (mediaUri == null) {
                            throw IllegalStateException("No media URI available for upload")
                        }

                        storageRef.putFile(mediaUri).await()
                        val mediaUrl = storageRef.downloadUrl.await().toString()

                        val thumbnailUrl = noteMediaDetail.thumbnailBitmap?.let {
                            val thumbnailId = UUID.randomUUID().toString()
                            val thumbnailRef =
                                storage.reference.child("$folderName/$thumbnailId.jpg")

                            ByteArrayOutputStream().use { baos ->
                                it.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                                thumbnailRef.putBytes(baos.toByteArray()).await()
                            }

                            thumbnailRef.downloadUrl.await().toString()
                        }

                        MediaDetail(
                            photoUrl = if (!noteMediaDetail.isVideo) mediaUrl else null,
                            videoUrl = if (noteMediaDetail.isVideo) mediaUrl else null,
                            thumbnailUrl = thumbnailUrl,
                            isVideo = noteMediaDetail.isVideo,
                            text = noteMediaDetail.text
                        )
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
            "smartcameraphoto_${timeStamp}",
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
        val videoFile = File.createTempFile(
            "smartcameravideo_${timeStamp}",
            ".mp4",
            storageDir
        )

        return getUriForFile(
            context,
            "com.jiahan.smartcamera.fileprovider",
            videoFile
        )
    }

    fun updatePostText(text: String) {
        _postText.value = text
        validatePostText(text)
    }

    fun updateUriList(context: Context, uriList: List<Uri>) {
        viewModelScope.launch {
            val newMediaDetailList = uriList.mapNotNull { uri ->
                try {
                    val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true

                    val bitmap = if (isVideo) getVideoThumbnail(context, uri) else null
                    val detectedText =
                        if (isVideo) null else detectLabelsAndJapaneseText(context, uri)

                    NoteMediaDetail(
                        photoUri = if (!isVideo) uri else null,
                        videoUri = if (isVideo) uri else null,
                        thumbnailBitmap = if (isVideo) bitmap else null,
                        isVideo = isVideo,
                        text = detectedText
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            _mediaList.value = newMediaDetailList + _mediaList.value
        }
    }

    fun removeUriFromList(index: Int) {
        if (index >= 0 && index < _mediaList.value.size) {
            val newList = _mediaList.value.toMutableList()
            newList.removeAt(index)
            _mediaList.value = newList
        }
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

    fun getVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        return _videoThumbnails.getOrPut(uri) {
            createVideoThumbnail(context, uri)
        }
    }

    suspend fun generateDescription(imageUri: Uri) =
        searchRepository.prepareAndStartImageDescription(imageUri)

    suspend fun detectLabelsAndJapaneseText(context: Context, image: Uri): String {
        return coroutineScope {
            val inputImage = InputImage.fromFilePath(context, image)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            val japaneseRecognizer = TextRecognition.getClient(
                JapaneseTextRecognizerOptions.Builder().build()
            )
            val descriptionDeferred = async {
                runCatching {
                    generateDescription(image)
                }
            }

            val labelDeferred = async {
                runCatching {
                    labeler.process(inputImage).await()
                }
            }

            val textDeferred = async {
                runCatching {
                    japaneseRecognizer.process(inputImage).await()
                }
            }

            val descriptionResult = descriptionDeferred.await()
            val labelResult = labelDeferred.await()
            val textResult = textDeferred.await()

            val description =
                descriptionResult.getOrNull()?.takeIf { it.isNotEmpty() } ?: ""

            val labelText = labelResult.getOrNull()?.joinToString("\n") { it.text }

            val visionText = textResult.getOrNull()?.text

            "$description$labelText$visionText"
        }
    }
}