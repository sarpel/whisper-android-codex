package com.app.whisper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.app.whisper.presentation.ui.TranscriptionScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.app.whisper.ui.components.WhisperTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.whisper.presentation.ui.ModelManagementScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val isDark by themeViewModel.isDark.collectAsState()
            WhisperTheme(darkTheme = isDark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "transcription") {
                        composable("transcription") {
                            TranscriptionScreen(onOpenModels = { navController.navigate("models") })
                        }
                        composable("models") {
                            ModelManagementScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
