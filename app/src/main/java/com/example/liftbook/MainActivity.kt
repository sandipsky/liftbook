package com.example.liftbook

import android.content.Intent
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
import com.example.liftbook.domain.calculator.TimeOfDay
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.notification.reminder.ReminderIntents
import com.example.liftbook.ui.components.workoutNameRes
import com.example.liftbook.ui.feature.reminders.ScheduledWorkoutViewModel
import com.example.liftbook.ui.navigation.LiftBookNavHost
import com.example.liftbook.ui.theme.LiftBookTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var clock: Clock

    private val viewModel: MainViewModel by viewModels()

    /** The same instance the navigation host uses: both are scoped to this activity. */
    private val scheduledWorkout: ScheduledWorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recreated after a rotation or process death, the intent has been answered already.
        if (savedInstanceState == null) answerReminder(intent)
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

    /** A reminder's tap reaching the app while it's already open (FR-7.3, FR-7.4). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        answerReminder(intent)
    }

    private fun answerReminder(intent: Intent) {
        // Reopened from Recents, the intent is the one that first opened the app, long since answered.
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        val launch = ReminderIntents.parse(intent) ?: return
        // A workout started with no routine is named for when it starts, as on Home: "Evening workout".
        val emptyWorkoutName = getString(TimeOfDay.of(LocalTime.now(clock)).workoutNameRes())
        scheduledWorkout.onLaunch(launch, emptyWorkoutName)
    }

    private companion object {
        // The platform's own scrims for three-button navigation, as enableEdgeToEdge() uses by default.
        val LIGHT_NAVIGATION_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_NAVIGATION_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
