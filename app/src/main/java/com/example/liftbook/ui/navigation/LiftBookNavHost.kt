package com.example.liftbook.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.liftbook.ui.components.ActiveWorkoutBanner
import com.example.liftbook.ui.feature.exercises.ArchivedExercisesRoute
import com.example.liftbook.ui.feature.exercises.ExerciseDetailRoute
import com.example.liftbook.ui.feature.exercises.ExerciseEditorRoute
import com.example.liftbook.ui.feature.exercises.ExerciseLibraryRoute
import com.example.liftbook.ui.feature.exercises.ExerciseLibraryViewModel
import com.example.liftbook.ui.feature.history.HistoryRoute
import com.example.liftbook.ui.feature.history.WorkoutDetailRoute
import com.example.liftbook.ui.feature.home.HomeRoute
import com.example.liftbook.ui.feature.progress.BodyWeightRoute
import com.example.liftbook.ui.feature.progress.ExerciseProgressRoute
import com.example.liftbook.ui.feature.progress.ProgressRoute
import com.example.liftbook.ui.feature.routines.RoutineDetailRoute
import com.example.liftbook.ui.feature.routines.RoutineEditorRoute
import com.example.liftbook.ui.feature.summary.WorkoutSummaryRoute
import com.example.liftbook.ui.feature.workout.ActiveWorkoutBannerState
import com.example.liftbook.ui.feature.workout.ActiveWorkoutBannerViewModel
import com.example.liftbook.ui.feature.workout.ActiveWorkoutRoute
import com.example.liftbook.ui.feature.workout.WorkoutEditorRoute
import com.example.liftbook.ui.theme.Spacing
import java.time.Instant

/**
 * The app shell and navigation graph (architecture §4.1). The top-level tabs share a bottom bar
 * — with the workout in progress docked above it — and every other destination is full screen,
 * so the bar leaves as a detail screen arrives.
 */
@Composable
fun LiftBookNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    bannerViewModel: ActiveWorkoutBannerViewModel = hiltViewModel(),
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelDestination.of(entry?.destination)
    val activeWorkout by bannerViewModel.activeWorkout.collectAsStateWithLifecycle()
    val now by bannerViewModel.now.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            MainBottomBar(
                currentTab = currentTab,
                activeWorkout = activeWorkout,
                now = { now },
                onSelectTab = navController::navigateToTab,
                onResumeWorkout = { navController.navigate(Route.ActiveWorkout) { launchSingleTop = true } },
            )
        },
        // Each screen's own Scaffold handles the system bars; this one only makes room for its bar.
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { if (isTabSwitch()) fadeIn(tween(NAV_MILLIS)) else sharedAxisEnter(SlideDirection.Start) },
            exitTransition = { if (isTabSwitch()) fadeOut(tween(NAV_MILLIS)) else sharedAxisExit(SlideDirection.Start) },
            popEnterTransition = { if (isTabSwitch()) fadeIn(tween(NAV_MILLIS)) else sharedAxisEnter(SlideDirection.End) },
            popExitTransition = { if (isTabSwitch()) fadeOut(tween(NAV_MILLIS)) else sharedAxisExit(SlideDirection.End) },
        ) {
            composable<Route.Home> { entry ->
                HomeRoute(
                    onOpenRoutine = { id -> entry.ifResumed { navController.navigate(Route.RoutineDetail(id)) } },
                    onCreateRoutine = { entry.ifResumed { navController.navigate(Route.RoutineEditor()) } },
                    onOpenWorkout = { entry.ifResumed { navController.navigate(Route.ActiveWorkout) { launchSingleTop = true } } },
                )
            }
            composable<Route.ExerciseLibrary> { entry ->
                ExerciseLibraryRoute(
                    onExerciseClick = { id -> entry.ifResumed { navController.navigate(Route.ExerciseDetail(id)) } },
                    onCreateExercise = { name -> entry.ifResumed { navController.navigate(Route.ExerciseEditor(initialName = name)) } },
                    onOpenArchived = { entry.ifResumed { navController.navigate(Route.ArchivedExercises) } },
                )
            }
            composable<Route.ExerciseDetail> { entry ->
                val route = entry.toRoute<Route.ExerciseDetail>()
                ExerciseDetailRoute(
                    exerciseId = route.exerciseId,
                    onNavigateUp = { entry.ifResumed { navController.navigateUp() } },
                    onEdit = { id -> entry.ifResumed { navController.navigate(Route.ExerciseEditor(exerciseId = id)) } },
                    onArchived = { id ->
                        // Hand the id back so the library can offer Undo where the user lands.
                        navController.previousBackStackEntry?.savedStateHandle?.set(ExerciseLibraryViewModel.ARCHIVED_EXERCISE_ID, id)
                        entry.ifResumed { navController.popBackStack() }
                    },
                    onOpenWorkout = { id -> entry.ifResumed { navController.navigate(Route.WorkoutDetail(id)) } },
                    onOpenProgress = { id -> entry.ifResumed { navController.navigate(Route.ExerciseProgress(id)) } },
                )
            }
            composable<Route.ExerciseEditor> { entry ->
                val route = entry.toRoute<Route.ExerciseEditor>()
                ExerciseEditorRoute(
                    exerciseId = route.exerciseId,
                    initialName = route.initialName,
                    onClose = { entry.ifResumed { navController.navigateUp() } },
                    // The library and detail screens observe the database, so they already show the change.
                    onSaved = { entry.ifResumed { navController.navigateUp() } },
                )
            }
            composable<Route.ArchivedExercises> { entry ->
                ArchivedExercisesRoute(
                    onNavigateUp = { entry.ifResumed { navController.navigateUp() } },
                    onExerciseClick = { id -> entry.ifResumed { navController.navigate(Route.ExerciseDetail(id)) } },
                )
            }
            composable<Route.RoutineDetail> { entry ->
                val route = entry.toRoute<Route.RoutineDetail>()
                RoutineDetailRoute(
                    routineId = route.routineId,
                    onNavigateUp = { entry.ifResumed { navController.navigateUp() } },
                    onEdit = { id -> entry.ifResumed { navController.navigate(Route.RoutineEditor(id)) } },
                    onOpenExercise = { id -> entry.ifResumed { navController.navigate(Route.ExerciseDetail(id)) } },
                    onOpenWorkout = {
                        entry.ifResumed {
                            // Back from the workout goes Home, where the banner keeps it one tap away.
                            navController.navigate(Route.ActiveWorkout) {
                                popUpTo<Route.Home>()
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenRoutine = { id -> entry.ifResumed { navController.navigate(Route.RoutineDetail(id)) } },
                    onDeleted = { entry.ifResumed { navController.popBackStack() } },
                )
            }
            composable<Route.RoutineEditor> { entry ->
                val route = entry.toRoute<Route.RoutineEditor>()
                RoutineEditorRoute(
                    routineId = route.routineId,
                    onClose = { entry.ifResumed { navController.navigateUp() } },
                    // Home and the routine's page observe the database, so they already show the change.
                    onSaved = { entry.ifResumed { navController.navigateUp() } },
                )
            }
            composable<Route.ActiveWorkout> { entry ->
                ActiveWorkoutRoute(
                    onNavigateUp = { entry.ifResumed { navController.navigateUp() } },
                    onDiscarded = { entry.ifResumed { navController.popBackStack() } },
                    onFinished = { workoutId ->
                        // Back from the summary goes Home, never into a finished workout (§4.2).
                        navController.navigate(Route.WorkoutSummary(workoutId)) {
                            popUpTo<Route.ActiveWorkout> { inclusive = true }
                        }
                    },
                )
            }
            composable<Route.WorkoutSummary> { entry ->
                val route = entry.toRoute<Route.WorkoutSummary>()
                WorkoutSummaryRoute(
                    workoutId = route.workoutId,
                    onDone = { entry.ifResumed { navController.popBackStack() } },
                )
            }
            composable<Route.History> { entry ->
                HistoryRoute(
                    onOpenWorkout = { id -> entry.ifResumed { navController.navigate(Route.WorkoutDetail(id)) } },
                    onStartWorkout = { entry.ifResumed { navController.navigateToTab(TopLevelDestination.Workout) } },
                )
            }
            composable<Route.WorkoutDetail> { entry ->
                val route = entry.toRoute<Route.WorkoutDetail>()
                WorkoutDetailRoute(
                    workoutId = route.workoutId,
                    onNavigateUp = { entry.ifResumed { navController.navigateUp() } },
                    onEdit = { id -> entry.ifResumed { navController.navigate(Route.WorkoutEditor(id)) } },
                    onOpenExercise = { id -> entry.ifResumed { navController.navigate(Route.ExerciseDetail(id)) } },
                    onDeleted = { entry.ifResumed { navController.popBackStack() } },
                )
            }
            composable<Route.WorkoutEditor> { entry ->
                val route = entry.toRoute<Route.WorkoutEditor>()
                WorkoutEditorRoute(
                    workoutId = route.workoutId,
                    onClose = { entry.ifResumed { navController.navigateUp() } },
                    // The workout's page observes the database, so it already shows the change.
                    onSaved = { entry.ifResumed { navController.navigateUp() } },
                    onDeleted = {
                        entry.ifResumed {
                            // The workout's page would only say it's gone, so go back past it.
                            if (!navController.popBackStack<Route.WorkoutDetail>(inclusive = true)) navController.popBackStack()
                        }
                    },
                )
            }
            composable<Route.Progress> { entry ->
                ProgressRoute(
                    onOpenExercise = { id -> entry.ifResumed { navController.navigate(Route.ExerciseProgress(id)) } },
                    onOpenBodyWeight = { entry.ifResumed { navController.navigate(Route.BodyWeight) } },
                    onStartWorkout = { entry.ifResumed { navController.navigateToTab(TopLevelDestination.Workout) } },
                )
            }
            composable<Route.ExerciseProgress> { entry ->
                val route = entry.toRoute<Route.ExerciseProgress>()
                ExerciseProgressRoute(
                    exerciseId = route.exerciseId,
                    onNavigateUp = { entry.ifResumed { navController.navigateUp() } },
                    onOpenWorkout = { id -> entry.ifResumed { navController.navigate(Route.WorkoutDetail(id)) } },
                )
            }
            composable<Route.BodyWeight> { entry ->
                BodyWeightRoute(onNavigateUp = { entry.ifResumed { navController.navigateUp() } })
            }
        }
    }
}

/**
 * The tabs, with the workout in progress docked above them. Both leave together when a
 * full-screen destination opens; while they animate out they keep showing what they last showed.
 */
@Composable
private fun MainBottomBar(
    currentTab: TopLevelDestination?,
    activeWorkout: ActiveWorkoutBannerState?,
    now: () -> Instant,
    onSelectTab: (TopLevelDestination) -> Unit,
    onResumeWorkout: () -> Unit,
) {
    val shownTab = rememberLastNonNull(currentTab)
    val shownWorkout = rememberLastNonNull(activeWorkout)
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    AnimatedVisibility(
        visible = currentTab != null,
        enter = fadeIn(tween(NAV_MILLIS)) + expandVertically(tween(NAV_MILLIS), expandFrom = Alignment.Top),
        exit = fadeOut(tween(NAV_MILLIS)) + shrinkVertically(tween(NAV_MILLIS), shrinkTowards = Alignment.Top),
    ) {
        Column(Modifier.background(barColor)) {
            AnimatedVisibility(
                visible = activeWorkout != null,
                enter = fadeIn(tween(NAV_MILLIS)) + expandVertically(tween(NAV_MILLIS)),
                exit = fadeOut(tween(NAV_MILLIS)) + shrinkVertically(tween(NAV_MILLIS)),
            ) {
                shownWorkout?.let { workout ->
                    ActiveWorkoutBanner(
                        name = workout.name,
                        startedAt = workout.startedAt,
                        rest = workout.rest,
                        now = now,
                        onClick = onResumeWorkout,
                        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs),
                    )
                }
            }
            NavigationBar(containerColor = barColor) {
                TopLevelDestination.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == shownTab,
                        onClick = { if (tab != currentTab) onSelectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        }
    }
}

/** [value], or while it's null the last value that wasn't — for content that animates out. */
@Composable
private fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

/** Tabs keep their own back stacks and state, as the Material navigation bar pattern expects. */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    TopLevelDestination.of(initialState.destination) != null && TopLevelDestination.of(targetState.destination) != null

// Material's shared-axis pattern, kept short: a small slide plus a cross-fade. Tabs just cross-fade.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisEnter(direction: SlideDirection): EnterTransition =
    fadeIn(tween(NAV_MILLIS)) +
        slideIntoContainer(direction, tween(NAV_MILLIS, easing = FastOutSlowInEasing)) { it / NAV_OFFSET_DIVISOR }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisExit(direction: SlideDirection): ExitTransition =
    fadeOut(tween(NAV_MILLIS)) +
        slideOutOfContainer(direction, tween(NAV_MILLIS, easing = FastOutSlowInEasing)) { it / NAV_OFFSET_DIVISOR }

/**
 * Runs [block] only while this destination is resumed. A double tap, or a tap during a transition,
 * would otherwise navigate twice.
 */
private inline fun NavBackStackEntry.ifResumed(block: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) block()
}

private const val NAV_MILLIS = 250
private const val NAV_OFFSET_DIVISOR = 8
