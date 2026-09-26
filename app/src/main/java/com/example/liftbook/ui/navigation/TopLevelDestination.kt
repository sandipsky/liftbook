package com.example.liftbook.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.example.liftbook.R
import kotlin.reflect.KClass

/** The bottom-bar tabs (architecture §4.1): four, which is what one-handed use wants. */
enum class TopLevelDestination(
    val route: Route,
    private val routeClass: KClass<out Route>,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Workout(Route.Home, Route.Home::class, R.string.nav_workout, Icons.Outlined.FitnessCenter),
    History(Route.History, Route.History::class, R.string.nav_history, Icons.Outlined.History),
    Progress(Route.Progress, Route.Progress::class, R.string.nav_progress, Icons.AutoMirrored.Outlined.ShowChart),
    Exercises(Route.ExerciseLibrary, Route.ExerciseLibrary::class, R.string.nav_exercises, Icons.AutoMirrored.Outlined.MenuBook),
    ;

    fun matches(destination: NavDestination?): Boolean = destination?.hasRoute(routeClass) == true

    companion object {
        fun of(destination: NavDestination?): TopLevelDestination? = entries.firstOrNull { it.matches(destination) }
    }
}
