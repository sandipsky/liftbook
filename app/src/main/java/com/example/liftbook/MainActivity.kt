package com.example.liftbook

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.ui.navigation.LiftBookNavHost
import com.example.liftbook.ui.theme.LiftBookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            // Until the setting is read — a few milliseconds — the launch window's background shows.
            val mode = themeMode ?: return@setContent
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The system bars' icons follow the app's theme, which may not be the system's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAVIGATION_SCRIM, DARK_NAVIGATION_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            LiftBookTheme(darkTheme = darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LiftBookNavHost()
                }
            }
        }
    }

    private companion object {
        // The platform's own scrims for three-button navigation, as enableEdgeToEdge() uses by default.
        val LIGHT_NAVIGATION_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_NAVIGATION_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
