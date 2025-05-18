package com.jiahan.smartcamera.search

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.database.data.DatabasePhoto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val state = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    var searchText by rememberSaveable { mutableStateOf("") }
    val filteredPhotos by viewModel.searchPhotos(searchText).collectAsState(initial = emptyList())

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            isRefreshing = true
            delay(500)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TextField(
                value = searchText,
                onValueChange = { text -> searchText = text },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(size = 12.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp)
                    )
                },
                placeholder = { Text(text = "Search photos") },
                colors = TextFieldDefaults.colors(
                    cursorColor = Color.Gray,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
            ),
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
        ) {
            if (filteredPhotos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No photos found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredPhotos.size) { index ->
                        val photo = filteredPhotos[index]
                        PhotoItem(photo = photo, navController = navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoItem(
    photo: DatabasePhoto,
    viewmodel: SearchViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val coroutineScope = rememberCoroutineScope()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            .clickable(true) {
                navController.navigate(
                    Screen.ImagePreview.createRoute(
                        imageUri = Uri.encode(photo.path),
                        text = Uri.encode(photo.title),
                        detect = false
                    )
                )
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = photo.path,
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            viewmodel.deleteImage(photo.id)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Delete photo",
                        tint = Color.White
                    )
                }

            }

            Text(
                text = photo.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                maxLines = 10
            )

            Text(
                text = "Saved: ${formatDate(photo.saveDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
}