package com.jiahan.smartcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jiahan.smartcamera.domain.AppUpdateState
import com.jiahan.smartcamera.home.HomeRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "noteId"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOutDuration = 500L
            val scaleAnim = splashScreenView.iconView.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(0f)
                .setDuration(fadeOutDuration)

            val fadeOut = splashScreenView.view.animate()
                .alpha(0f)
                .setDuration(fadeOutDuration)
                .withEndAction { splashScreenView.remove() }

            scaleAnim.start()
            fadeOut.start()
        }

        super.onCreate(savedInstanceState)

        viewModel.handleIncomingIntent(intent)
        handleNotificationIntent(intent)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val startDestination = uiState.startDestination
            val isAppReady = uiState.isAppReady
            val showBottomBar = uiState.showBottomBar
            val scrollToTop = uiState.scrollToTop
            val hasPendingShare by viewModel.hasPendingShare.collectAsStateWithLifecycle()
            val pendingNoteId = uiState.pendingNoteId
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()

            splashScreen.setKeepOnScreenCondition { !isAppReady }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}

            // startFlexibleUpdate() shows Play Store's own confirmation UI before downloading,
            // so this only needs to kick off the flow -- no extra in-app prompt required. The
            // launcher result is ignored because there is nothing here to do with it: cancelling
            // leaves updateState on Available, and the effect below is *keyed* on that state, so it
            // does not re-run. A keyed LaunchedEffect fires on key change, never on recomposition,
            // and AppUpdateState.Available is a data object -- a re-emission is equal, so the key
            // does not change either. The offer is therefore made once per transition into
            // Available and not repeated for the rest of the session, which is the intent: Play
            // already showed its own dialog and re-prompting a user who declined would nag.
            val appUpdateLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) {}

            LaunchedEffect(startDestination) {
                if (startDestination == HomeRoute &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(updateState) {
                if (updateState is AppUpdateState.Available) {
                    viewModel.startFlexibleUpdate(appUpdateLauncher)
                }
            }

            SmartPhotosApp(
                isDarkTheme = isDarkTheme,
                isAppReady = isAppReady,
                startDestination = startDestination,
                showBottomBar = showBottomBar,
                scrollToTop = scrollToTop,
                hasPendingShare = hasPendingShare,
                pendingNoteId = pendingNoteId,
                isUpdateReadyToInstall = updateState is AppUpdateState.Downloaded,
                onScrollDirectionChanged = viewModel::updateBottomBarVisibility,
                onScrollToTopConsumed = viewModel::consumeScrollToTopEvent,
                onTriggerScrollToTop = viewModel::triggerScrollToTop,
                onUpdateStartDestination = viewModel::updateStartDestination,
                onPendingNoteIdConsumed = viewModel::consumePendingNoteId,
                onCompleteUpdate = viewModel::completeUpdate,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_NOTE_ID)?.let { noteId ->
            viewModel.onNotificationNoteIdReceived(noteId)
        }
    }
}