package com.example.liftbook.ui.feature.exercises

import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressPoint
import com.example.liftbook.domain.model.WeightUnit
import java.time.LocalDate
import java.time.ZoneId

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means no exercise has this id. */
    val exercise: Exercise? = null,
    /** The last-performed values (FR-1.5); null if the exercise has never been done. */
    val lastSession: ExerciseSession? = null,
    /** The exercise's main number over all time, leading to its charts (FR-5.1); null with nothing to chart. */
    val progress: ProgressPreview? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    /** The zone workout start times are read in, to place them on a calendar day. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** A glance at one metric — the default for the exercise's type — with its values, oldest first. */
data class ProgressPreview(
    val metric: ProgressMetric,
    val points: List<ProgressPoint>,
)

sealed interface ExerciseDetailAction {
    data object NavigateUp : ExerciseDetailAction

    /** Opens the exercise's charts (FR-5.1). */
    data object OpenProgress : ExerciseDetailAction

    data object Edit : ExerciseDetailAction

    data object Archive : ExerciseDetailAction

    data object Restore : ExerciseDetailAction

    /** Opens the workout a past session was part of (FR-4.2). */
    data class OpenWorkout(val workoutId: String) : ExerciseDetailAction
}

sealed interface ExerciseDetailEvent {
    data class Archived(val exerciseId: String) : ExerciseDetailEvent
}
