package com.example.liftbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.liftbook.ui.navigation.LiftBookNavHost
import com.example.liftbook.ui.theme.LiftBookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiftBookTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LiftBookNavHost()
                }
            }
        }
    }
}
