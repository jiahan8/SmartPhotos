package com.jiahan.smartcamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.jiahan.smartcamera.database.dao.PhotoDao
import com.jiahan.smartcamera.database.data.DatabasePhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class SearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoDao: PhotoDao,
) : PhotoRepository {
    // Directory for storing images
    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply {
            if (!exists()) mkdirs()
        }
    }

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
    suspend fun saveImageFromUri(uri: Uri, title: String) {
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
    suspend fun deleteImage(imageId: Int) {
        withContext(Dispatchers.IO) {
            val image = photoDao.getImageById(imageId) ?: return@withContext
            val file = File(image.path)
            if (file.exists()) {
                file.delete()
            }
            photoDao.deleteImage(imageId)
        }
    }

    suspend fun isImageExists(path: String): Boolean {
        return photoDao.getPhotoByPath(path) != null
    }

}