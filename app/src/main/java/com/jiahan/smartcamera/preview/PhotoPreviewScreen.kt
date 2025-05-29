package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    photoSource: PhotoSource,
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val model = when (photoSource) {
                is PhotoSource.LocalUri -> photoSource.uri
                is PhotoSource.RemoteUrl -> photoSource.url
            }
            AsyncImage(
                model = model,
                contentDescription = "Selected Image",
                modifier = Modifier.fillMaxSize(),
                onError = {
                    it.result.throwable.printStackTrace()
                }
            )
        }
    }
}

sealed class PhotoSource {
    data class LocalUri(val uri: Uri) : PhotoSource()
    data class RemoteUrl(val url: String) : PhotoSource()
}