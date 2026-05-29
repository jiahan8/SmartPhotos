package com.jiahan.smartcamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
            val isAppReady by viewModel.isAppReady.collectAsStateWithLifecycle()
            val showBottomBar by viewModel.showBottomBar.collectAsStateWithLifecycle()
            val scrollToTop by viewModel.scrollToTop.collectAsStateWithLifecycle()

            splashScreen.setKeepOnScreenCondition { !isAppReady }

            SmartPhotosApp(
                isDarkTheme = isDarkTheme,
                isAppReady = isAppReady,
                startDestination = startDestination,
                showBottomBar = showBottomBar,
                scrollToTop = scrollToTop,
                onScrollDirectionChanged = viewModel::updateBottomBarVisibility,
                onScrollToTopConsumed = viewModel::consumeScrollToTopEvent,
                onTriggerScrollToTop = viewModel::triggerScrollToTop,
                onUpdateStartDestination = viewModel::updateStartDestination,
            )
        }
    }
}