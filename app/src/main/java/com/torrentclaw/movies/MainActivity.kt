package com.torrentclaw.movies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torrentclaw.movies.ui.MainViewModel
import com.torrentclaw.movies.ui.MainUiState
import com.torrentclaw.movies.ui.navigation.TorrentClawNavGraph
import com.torrentclaw.movies.ui.theme.TorrentClawTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the splash hand-off is seamless.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var keepSplashVisible = true
        splash.setKeepOnScreenCondition { keepSplashVisible }

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            keepSplashVisible = !uiState.isReady

            TorrentClawTheme(
                themeMode = uiState.themeMode,
                dynamicColor = uiState.dynamicColor,
            ) {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (uiState.isReady) {
                        TorrentClawNavGraph()
                    }
                }
            }
        }
    }
}
