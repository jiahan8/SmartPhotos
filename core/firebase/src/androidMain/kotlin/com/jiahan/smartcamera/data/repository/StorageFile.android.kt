package com.jiahan.smartcamera.data.repository

import android.net.Uri
import com.jiahan.smartcamera.domain.MediaUri
import dev.gitlive.firebase.storage.File

internal actual fun MediaUri.toStorageFile(): File = File(Uri.parse(value))