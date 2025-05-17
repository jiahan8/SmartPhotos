package com.jiahan.smartcamera.imagepreview

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ImagePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val encodedUri: String = checkNotNull(savedStateHandle[Screen.ImagePreview.URI_ARG])
    val imageUri = Uri.decode(encodedUri).toUri()
    private val text: String =
        Uri.decode(savedStateHandle.getStateFlow(Screen.ImagePreview.TEXT_ARG, "").value)
    val shouldDetectImage =
        savedStateHandle.getStateFlow(Screen.ImagePreview.DETECT_IMAGE_ARG, false).value

    private val _detectedText = MutableStateFlow(text)
    val detectedText: StateFlow<String> = _detectedText
    private val _isImageSaved = MutableStateFlow(false)
    val isImageSaved: StateFlow<Boolean> = _isImageSaved

    suspend fun detectLabelsAndJapaneseText(context: Context, image: Uri) {
        val inputImage = InputImage.fromFilePath(context, image)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val japaneseRecognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )

        coroutineScope {
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
                descriptionResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { "$it\n\n" } ?: ""

            val labelText = labelResult.getOrNull()?.joinToString("\n") {
                "Label: ${it.text}, Confidence: ${"%.2f".format(it.confidence)}"
            } ?: "Image labeling failed: ${labelResult.exceptionOrNull()?.localizedMessage}"

            val visionText = textResult.getOrNull()?.text
                ?: "Text recognition failed: ${textResult.exceptionOrNull()?.localizedMessage}"

            _detectedText.value = "$description$labelText\n\n$visionText"
        }
    }

    suspend fun saveImage(imageUri: Uri, title: String) {
        searchRepository.saveImageFromUri(imageUri, title)
    }

    suspend fun isImageSaved(imageUri: Uri): Boolean {
        val result = searchRepository.isImageExists(imageUri.toString())
        _isImageSaved.value = result
        return result
    }

    suspend fun generateDescription(imageUri: Uri) =
        searchRepository.prepareAndStartImageDescription(imageUri)

    override fun onCleared() {
        super.onCleared()
        searchRepository.closeImageDescriber()
    }
}