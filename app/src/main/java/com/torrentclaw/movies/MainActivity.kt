package com.torrentclaw.movies

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torrentclaw.movies.ui.MainViewModel
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

        // Android 13+ needs an explicit grant before the download notification can
        // appear. Asked once, non-blocking: the transfer still runs without it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Hold the splash until the first DataStore emission so the app opens
            // straight into the correct theme instead of flashing the default one.
            splash.setKeepOnScreenCondition { !uiState.isReady }

            TorrentClawTheme(
                themeMode = uiState.themeMode,
                dynamicColor = uiState.dynamicColor,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TorrentClawNavGraph()
                }
            }
        }
    }

    private companion object {
        const val NOTIFICATION_REQUEST = 101
    }
}
