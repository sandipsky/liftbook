package com.example.liftbook.ui.feature.exercises

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.ui.components.ChoiceChip
import com.example.liftbook.ui.components.DropdownFilterChip
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.SearchField
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.labelRes
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import kotlinx.coroutines.launch

@Composable
fun ExerciseLibraryRoute(
    onExerciseClick: (exerciseId: String) -> Unit,
    onCreateExercise: (name: String?) -> Unit,
    onOpenArchived: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExerciseLibraryEvent.ExerciseArchived -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.exercise_archived_message, event.name),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onAction(ExerciseLibraryAction.RestoreExercise(event.exerciseId))
                    }
                }
            }
        }
    }

    ExerciseLibraryScreen(
        state = state,
        queryState = viewModel.queryState,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                is ExerciseLibraryAction.OpenExercise -> onExerciseClick(action.exerciseId)
                is ExerciseLibraryAction.CreateExercise -> onCreateExercise(action.name)
                ExerciseLibraryAction.OpenArchived -> onOpenArchived()
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
fun ExerciseLibraryScreen(
    state: ExerciseLibraryUiState,
    queryState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (ExerciseLibraryAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 || listState.lastScrolledBackward }
    }
    var openFilter by rememberSaveable { mutableStateOf<LibraryFilter?>(null) }
    val content = when {
        state.isLoading -> LibraryContent.Loading
        state.resultCount == 0 -> LibraryContent.Empty
        else -> LibraryContent.Results
    }

    Scaffold(
        topBar = {
            LibraryHeader(
                state = state,
                queryState = queryState,
                isScrolled = isScrolled,
                onOpenFilter = { openFilter = it },
                onOpenArchived = { onAction(ExerciseLibraryAction.OpenArchived) },
            )
        },
        floatingActionButton = {
            // An empty state carries its own action; one primary action per screen.
            AnimatedVisibility(
                visible = content != LibraryContent.Empty,
                enter = fadeIn(tween(CONTENT_FADE_MILLIS)) + scaleIn(tween(CONTENT_FADE_MILLIS), initialScale = FAB_HIDDEN_SCALE),
                exit = fadeOut(tween(CONTENT_FADE_MILLIS)) + scaleOut(tween(CONTENT_FADE_MILLIS), targetScale = FAB_HIDDEN_SCALE),
            ) {
                val label = stringResource(R.string.exercises_new)
                ExtendedFloatingActionButton(
                    text = { Text(label) },
                    icon = {
                        Icon(Icons.Outlined.Add, contentDescription = if (fabExpanded) null else label)
                    },
                    expanded = fabExpanded,
                    onClick = { onAction(ExerciseLibraryAction.CreateExercise(name = null)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "libraryContent",
        ) { target ->
            when (target) {
                LibraryContent.Loading -> LibrarySkeleton(Modifier.padding(padding))
                LibraryContent.Empty -> LibraryEmpty(state = state, onAction = onAction, modifier = Modifier.padding(padding))
                LibraryContent.Results -> LibraryList(state = state, listState = listState, padding = padding, onAction = onAction)
            }
        }
    }

    openFilter?.let { filter ->
        FilterSheet(filter = filter, state = state, onAction = onAction, onDismiss = { openFilter = null })
    }
}

private enum class LibraryContent { Loading, Empty, Results }

private enum class LibraryFilter { Muscle, Equipment }

/**
 * Title, search and filters stay pinned together. The title shares the bar with its action
 * instead of sitting below an empty row, so the list starts high enough to be browsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryHeader(
    state: ExerciseLibraryUiState,
    queryState: TextFieldState,
    isScrolled: Boolean,
    onOpenFilter: (LibraryFilter) -> Unit,
    onOpenArchived: () -> Unit,
) {
    // One surface-tone shift for the whole header once the list scrolls under it.
    val containerColor by animateColorAsState(
        targetValue = if (isScrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(CONTENT_FADE_MILLIS),
        label = "headerColor",
    )
    Column(Modifier.background(containerColor)) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.exercises_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            },
            actions = { LibraryMenu(onOpenArchived = onOpenArchived) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
        SearchField(
            state = queryState,
            placeholder = if (state.libraryCount > 0) {
                pluralStringResource(R.plurals.exercises_search_placeholder, state.libraryCount, state.libraryCount)
            } else {
                stringResource(R.string.exercises_search_placeholder_empty)
            },
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        FilterRow(
            state = state,
            onOpenFilter = onOpenFilter,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun LibraryMenu(onOpenArchived: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercises_menu_archived)) },
                leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenArchived()
                },
            )
        }
    }
}

@Composable
private fun FilterRow(
    state: ExerciseLibraryUiState,
    onOpenFilter: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownFilterChip(
            label = state.muscleFilter?.let { stringResource(it.labelRes()) }
                ?: stringResource(R.string.exercises_filter_muscle),
            active = state.muscleFilter != null,
            onClick = { onOpenFilter(LibraryFilter.Muscle) },
        )
        DropdownFilterChip(
            label = state.equipmentFilter?.let { stringResource(it.labelRes()) }
                ?: stringResource(R.string.exercises_filter_equipment),
            active = state.equipmentFilter != null,
            onClick = { onOpenFilter(LibraryFilter.Equipment) },
        )
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(
            visible = state.isFiltering && !state.isLoading,
            enter = fadeIn(tween(CONTENT_FADE_MILLIS)),
            exit = fadeOut(tween(CONTENT_FADE_MILLIS)),
        ) {
            Text(
                text = pluralStringResource(R.plurals.exercises_result_count, state.resultCount, state.resultCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Announces the count as the user types, so TalkBack users know a search worked.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun LibraryList(
    state: ExerciseLibraryUiState,
    listState: LazyListState,
    padding: PaddingValues,
    onAction: (ExerciseLibraryAction) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        // Top padding goes on the list itself, so sticky headers pin below the header, not under it.
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = padding.calculateTopPadding(),
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
            ),
        contentPadding = PaddingValues(top = Spacing.xs, bottom = padding.calculateBottomPadding() + FabClearance),
    ) {
        state.sections.forEach { section ->
            section.title?.let { title ->
                stickyHeader(key = "section-$title", contentType = "section") { SectionLetter(title) }
            }
            items(section.exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    onClick = { onAction(ExerciseLibraryAction.OpenExercise(exercise.id)) },
                )
            }
        }
    }
}

@Composable
private fun SectionLetter(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun LibraryEmpty(
    state: ExerciseLibraryUiState,
    onAction: (ExerciseLibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasQuery = state.query.isNotEmpty()
    EmptyState(
        icon = if (state.isFiltering) Icons.Outlined.SearchOff else Icons.Outlined.Inventory2,
        title = when {
            hasQuery -> stringResource(R.string.exercises_empty_query_title, state.query)
            state.hasFilters -> stringResource(R.string.exercises_empty_filters_title)
            else -> stringResource(R.string.exercises_empty_library_title)
        },
        body = when {
            hasQuery && state.hasFilters -> stringResource(R.string.exercises_empty_query_filters_body)
            hasQuery -> stringResource(R.string.exercises_empty_query_body)
            state.hasFilters -> stringResource(R.string.exercises_empty_filters_body)
            else -> stringResource(R.string.exercises_empty_library_body)
        },
        modifier = modifier.verticalScroll(rememberScrollState()),
        action = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                when {
                    hasQuery -> Button(onClick = { onAction(ExerciseLibraryAction.CreateExercise(state.query)) }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
                        Spacer(Modifier.size(Spacing.xs))
                        Text(stringResource(R.string.exercises_empty_create, state.query))
                    }
                    !state.hasFilters -> Button(onClick = { onAction(ExerciseLibraryAction.OpenArchived) }) {
                        Text(stringResource(R.string.exercises_menu_archived))
                    }
                }
                if (state.hasFilters) {
                    OutlinedButton(onClick = { onAction(ExerciseLibraryAction.ClearFilters) }) {
                        Text(stringResource(R.string.exercises_clear_filters))
                    }
                }
            }
        },
    )
}

@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.exercises_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(top = Spacing.xs)) {
            SkeletonWidths.forEach { (nameWidth, metaWidth) ->
                // Blocks sit inside the real rows' line heights, so nothing shifts when content arrives.
                Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                    SkeletonBlock(
                        Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(nameWidth).height(Spacing.md),
                    )
                    SkeletonBlock(
                        Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(metaWidth).height(Spacing.sm),
                    )
                }
            }
        }
    }
}

private val SkeletonWidths = listOf(
    0.58f to 0.3f, 0.46f to 0.36f, 0.66f to 0.28f, 0.52f to 0.34f,
    0.4f to 0.26f, 0.62f to 0.32f, 0.5f to 0.3f, 0.56f to 0.38f,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    filter: LibraryFilter,
    state: ExerciseLibraryUiState,
    onAction: (ExerciseLibraryAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val choose: (ExerciseLibraryAction) -> Unit = { action ->
        onAction(action)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg)) {
            Text(
                text = stringResource(
                    if (filter == LibraryFilter.Muscle) R.string.exercises_filter_muscle_title else R.string.exercises_filter_equipment_title,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                when (filter) {
                    LibraryFilter.Muscle -> {
                        ChoiceChip(
                            selected = state.muscleFilter == null,
                            onClick = { choose(ExerciseLibraryAction.FilterByMuscle(null)) },
                            label = stringResource(R.string.exercises_filter_any_muscle),
                        )
                        MuscleGroup.entries.forEach { muscle ->
                            ChoiceChip(
                                selected = state.muscleFilter == muscle,
                                onClick = { choose(ExerciseLibraryAction.FilterByMuscle(muscle)) },
                                label = stringResource(muscle.labelRes()),
                            )
                        }
                    }
                    LibraryFilter.Equipment -> {
                        ChoiceChip(
                            selected = state.equipmentFilter == null,
                            onClick = { choose(ExerciseLibraryAction.FilterByEquipment(null)) },
                            label = stringResource(R.string.exercises_filter_any_equipment),
                        )
                        Equipment.entries.forEach { equipment ->
                            ChoiceChip(
                                selected = state.equipmentFilter == equipment,
                                onClick = { choose(ExerciseLibraryAction.FilterByEquipment(equipment)) },
                                label = stringResource(equipment.labelRes()),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Keeps the last row clear of the floating action button: FAB height plus a margin. */
private val FabClearance = 56.dp + Spacing.xl

private const val CONTENT_FADE_MILLIS = 200
private const val FAB_HIDDEN_SCALE = 0.8f

private fun sampleState(
    query: String = "",
    muscle: MuscleGroup? = null,
    empty: Boolean = false,
): ExerciseLibraryUiState {
    val library = ExercisePreviewData.library
    return ExerciseLibraryUiState(
        isLoading = false,
        query = query,
        muscleFilter = muscle,
        sections = if (empty) {
            emptyList()
        } else {
            library.groupBy { it.name.first().uppercase() }.map { (initial, group) -> ExerciseSection(initial, group) }
        },
        resultCount = if (empty) 0 else library.size,
        libraryCount = 129,
    )
}

@ThemePreviews
@Composable
private fun ExerciseLibraryScreenPreview() {
    LiftBookTheme {
        ExerciseLibraryScreen(
            state = sampleState(),
            queryState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExerciseLibraryEmptySearchPreview() {
    LiftBookTheme {
        ExerciseLibraryScreen(
            state = sampleState(query = "zercher", muscle = MuscleGroup.QUADS, empty = true),
            queryState = rememberTextFieldState("zercher"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExerciseLibraryLoadingPreview() {
    LiftBookTheme {
        ExerciseLibraryScreen(
            state = ExerciseLibraryUiState(),
            queryState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
