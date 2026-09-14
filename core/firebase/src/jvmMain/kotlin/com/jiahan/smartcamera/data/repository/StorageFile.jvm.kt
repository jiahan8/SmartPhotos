package com.jiahan.smartcamera.data.repository

import android.net.Uri
import com.jiahan.smartcamera.domain.MediaUri
import dev.gitlive.firebase.storage.File

/** The JVM SDK GitLive builds on ships its own `android.net.Uri`, so this matches Android's. */
internal actual fun MediaUri.toStorageFile(): File = File(Uri.parse(value))