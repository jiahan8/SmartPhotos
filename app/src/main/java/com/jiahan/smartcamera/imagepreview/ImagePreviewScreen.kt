package com.jiahan.smartcamera.imagepreview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    navController: NavHostController,
    viewModel: ImagePreviewViewModel = hiltViewModel()
) {
    val imageUri = viewModel.imageUri
    val shouldDetectImage = viewModel.shouldDetectImage

    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val detectedText by viewModel.detectedText.collectAsState()
    val fabVisible = detectedText.isNotBlank()
    val isImageSaved by viewModel.isImageSaved.collectAsState()

    LaunchedEffect(imageUri) {
        if (shouldDetectImage)
            viewModel.detectLabelsAndJapaneseText(context, imageUri)
        viewModel.isImageSaved(imageUri)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Image Preview") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Selected Image",
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
            ) {
                if (fabVisible) {
                    IconButton(
                        onClick = { showSheet = true },
                        modifier = Modifier
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info"
                        )
                    }
                }
                if (shouldDetectImage) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (!isImageSaved) {
                                    viewModel.saveImage(imageUri, detectedText)
                                    viewModel.isImageSaved(imageUri) // Refresh the state
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector =
                                if (isImageSaved)
                                    Icons.Default.Favorite
                                else
                                    Icons.Default.FavoriteBorder,
                            contentDescription = if (isImageSaved) "Saved" else "Save"
                        )
                    }
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = bottomSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = if (detectedText.isBlank()) "No text detected." else detectedText
                    )
                }
            }
        }
    }
}