package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaUri
import dev.gitlive.firebase.storage.File
import platform.Foundation.NSURL

internal actual fun MediaUri.toStorageFile(): File =
    File(requireNotNull(NSURL.URLWithString(value)) { "Not a URL: $value" })