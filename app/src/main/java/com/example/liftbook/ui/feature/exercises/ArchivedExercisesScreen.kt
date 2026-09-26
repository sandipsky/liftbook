package com.example.liftbook.ui.feature.exercises

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

@Composable
fun ArchivedExercisesRoute(
    onNavigateUp: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    viewModel: ArchivedExercisesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ArchivedExercisesEvent.Restored ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.archived_restored_message, event.name))
            }
        }
    }

    ArchivedExercisesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                ArchivedExercisesAction.NavigateUp -> onNavigateUp()
                is ArchivedExercisesAction.OpenExercise -> onExerciseClick(action.exerciseId)
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedExercisesScreen(
    state: ArchivedExercisesUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (ArchivedExercisesAction) -> Unit,
) {
    Scaffold(
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.archived_title),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(ArchivedExercisesAction.NavigateUp) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = when {
            state.isLoading -> ArchivedContent.Loading
            state.exercises.isEmpty() -> ArchivedContent.Empty
            else -> ArchivedContent.List
        }
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "archivedContent",
        ) { target ->
            when (target) {
                ArchivedContent.Loading -> ArchivedSkeleton(Modifier.padding(padding))
                ArchivedContent.Empty -> EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = stringResource(R.string.archived_empty_title),
                    body = stringResource(R.string.archived_empty_body),
                    modifier = Modifier.padding(padding),
                )
                ArchivedContent.List -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + Spacing.lg,
                    ),
                ) {
                    item(key = "intro", contentType = "intro") {
                        Text(
                            text = stringResource(R.string.archived_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xs),
                        )
                    }
                    items(state.exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
                        val restoreDescription = stringResource(R.string.archived_restore_description, exercise.name)
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { onAction(ArchivedExercisesAction.OpenExercise(exercise.id)) },
                            // Restored rows leave with a short fade rather than vanishing.
                            modifier = Modifier.animateItem(),
                            trailing = {
                                TextButton(
                                    onClick = { onAction(ArchivedExercisesAction.Restore(exercise.id)) },
                                    modifier = Modifier.semantics { contentDescription = restoreDescription },
                                ) {
                                    Text(stringResource(R.string.action_restore))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class ArchivedContent { Loading, Empty, List }

@Composable
private fun ArchivedSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.archived_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(top = Spacing.xs)) {
            repeat(3) {
                Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                    SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.5f).height(Spacing.md))
                    SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.sm))
                }
            }
        }
    }
}

private const val CONTENT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun ArchivedExercisesPreview() {
    LiftBookTheme {
        ArchivedExercisesScreen(
            state = ArchivedExercisesUiState(
                isLoading = false,
                exercises = listOf(ExercisePreviewData.archivedCurl, ExercisePreviewData.rowing.copy(isArchived = true)),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ArchivedExercisesEmptyPreview() {
    LiftBookTheme {
        ArchivedExercisesScreen(
            state = ArchivedExercisesUiState(isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
