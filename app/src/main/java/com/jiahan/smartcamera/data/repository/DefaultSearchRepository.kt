package com.jiahan.smartcamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.jiahan.smartcamera.database.dao.PhotoDao
import com.jiahan.smartcamera.database.data.DatabasePhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class DefaultSearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoDao: PhotoDao,
) : SearchRepository {
    // Directory for storing images
    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply {
            if (!exists()) mkdirs()
        }
    }

    // Create an image describer
    val options = ImageDescriberOptions.builder(context).build()
    val imageDescriber = ImageDescription.getClient(options)

    override val photos: Flow<List<DatabasePhoto>> =
        photoDao.getPhotos()

    override suspend fun savePhoto(databasePhoto: DatabasePhoto) {
        photoDao.insertPhotos(databasePhoto)
    }

    // Save bitmap image to storage and reference to Room
    suspend fun saveImage(bitmap: Bitmap, title: String) {
        return withContext(Dispatchers.IO) {
            // Create a unique filename
            val filename = "IMG_${UUID.randomUUID()}.jpg"
            val file = File(imagesDir, filename)

            // Save the bitmap to file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
            }

            // Create and insert the database entry
            val imageEntity = DatabasePhoto(
                path = file.absolutePath,
                originalName = "",
                title = title,
                saveDate = System.currentTimeMillis()
            )

            photoDao.insertPhotos(imageEntity)
        }
    }

    // Save image from URI (like from gallery picker)
    override suspend fun saveImageFromUri(uri: Uri, title: String) {
        return withContext(Dispatchers.IO) {
            // Create a unique filename
            val filename = "IMG_${UUID.randomUUID()}.jpg"
            val file = File(imagesDir, filename)

            // Copy the content from URI to our file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            // Create and insert the database entry
            val imageEntity = DatabasePhoto(
                path = file.absolutePath,
                originalName = uri.toString(),
                title = title,
                saveDate = System.currentTimeMillis()
            )

            photoDao.insertPhotos(imageEntity)
        }
    }

    // Delete an image and its file
    override suspend fun deleteImage(imageId: Int) {
        withContext(Dispatchers.IO) {
            val image = photoDao.getImageById(imageId) ?: return@withContext
            val file = File(image.path)
            if (file.exists()) {
                file.delete()
            }
            photoDao.deleteImage(imageId)
        }
    }

    override suspend fun isImageExists(path: String): Boolean {
        return photoDao.getPhotoByPath(path) != null
    }

    override fun searchPhotos(query: String): Flow<List<DatabasePhoto>> =
        photoDao.searchPhotos(query)

    override suspend fun prepareAndStartImageDescription(
        imageUri: Uri
    ): String = try {
        val bitmap = uriToBitmap(imageUri)
        // Check feature availability, status will be one of the following:
        // UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, AVAILABLE
        val featureStatus = imageDescriber.checkFeatureStatus().await()

        when (featureStatus) {
            FeatureStatus.DOWNLOADABLE -> {
                suspendCancellableCoroutine { continuation ->
                    // Download feature if necessary.
                    // If downloadFeature is not called, the first inference request
                    // will also trigger the feature to be downloaded if it's not
                    // already downloaded.
                    imageDescriber.downloadFeature(object : DownloadCallback {
                        override fun onDownloadStarted(bytesToDownload: Long) {
                            bytesToDownload
                        }

                        override fun onDownloadFailed(e: GenAiException) {
                            continuation.resumeWith(Result.failure(e))
                        }

                        override fun onDownloadProgress(totalBytesDownloaded: Long) {
                            totalBytesDownloaded
                        }

                        override fun onDownloadCompleted() {
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = startImageDescriptionRequest(bitmap, imageDescriber)
                                continuation.resume(result) { cause, _, _ -> }
                            }
                        }
                    })
                }
            }

            FeatureStatus.DOWNLOADING -> {
                // Inference request will automatically run once feature is
                // downloaded.
                // If Gemini Nano is already downloaded on the device, the
                // feature-specific LoRA adapter model will be downloaded
                // very quickly. However, if Gemini Nano is not already
                // downloaded, the download process may take longer.
                startImageDescriptionRequest(bitmap, imageDescriber)
            }

            FeatureStatus.AVAILABLE -> {
                startImageDescriptionRequest(bitmap, imageDescriber)
            }

            else -> ""
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }

    suspend fun startImageDescriptionRequest(
        bitmap: Bitmap,
        imageDescriber: ImageDescriber
    ): String {
        // Create task request
        val imageDescriptionRequest = ImageDescriptionRequest
            .builder(bitmap)
            .build()

        // Run inference with a streaming callback
//        val imageDescriptionResultStreaming =
//            imageDescriber.runInference(imageDescriptionRequest) { outputText ->
//                // Append new output text to show in UI
//                // This callback is called incrementally as the description
//                // is generated
//                Toast.makeText(context, outputText, Toast.LENGTH_LONG).show()
//            }

        // You can also get a non-streaming response from the request
        return imageDescriber.runInference(imageDescriptionRequest).await().description
    }

    override fun closeImageDescriber() {
        imageDescriber.close()
    }

    /**
     * Converts a Uri to a Bitmap
     * @param uri The Uri to convert
     * @return The resulting Bitmap
     */
    private suspend fun uriToBitmap(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }

        return@withContext bitmap
    }
}