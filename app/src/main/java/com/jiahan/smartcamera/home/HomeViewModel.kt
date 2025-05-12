package com.jiahan.smartcamera.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.jiahan.smartcamera.repository.RemoteConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {

    private val _uploading = mutableStateOf(false)
    private val _detectedText = MutableStateFlow("")
    val detectedText: StateFlow<String> = _detectedText

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

    suspend fun detectLabelsAndJapaneseText(context: Context, image: Uri) {
        val inputImage = InputImage.fromFilePath(context, image)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val japaneseRecognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )

        coroutineScope {
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

            val labelResult = labelDeferred.await()
            val textResult = textDeferred.await()

            val labelText = labelResult.getOrNull()?.joinToString("\n") {
                "Label: ${it.text}, Confidence: ${"%.2f".format(it.confidence)}, Index: ${it.index}"
            } ?: "Image labeling failed: ${labelResult.exceptionOrNull()?.localizedMessage}"

            val visionText = textResult.getOrNull()?.text
                ?: "Text recognition failed: ${textResult.exceptionOrNull()?.localizedMessage}"

            _detectedText.value = "$labelText\n\n$visionText"
        }
    }
}