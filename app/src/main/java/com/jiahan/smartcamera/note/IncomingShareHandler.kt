package com.jiahan.smartcamera.note

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject

data class IncomingShare(val text: String?, val uris: List<Uri>)

class IncomingShareHandler @Inject constructor() {
    private val _incomingShare = MutableStateFlow<IncomingShare?>(null)
    val incomingShare = _incomingShare.asStateFlow()

    fun postShare(share: IncomingShare) {
        _incomingShare.value = share
    }

    fun consume(): IncomingShare? = _incomingShare.getAndUpdate { null }
}