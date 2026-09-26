package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import com.example.liftbook.domain.calculator.progress
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.ui.feature.routines.RoutinePreviewData
import java.time.Instant
import java.time.ZoneId

/** Sample data for the workout and summary previews: a Push day, part-way through. */
internal object WorkoutPreviewData {

    val zone: ZoneId = RoutinePreviewData.zone
    val startedAt: Instant = RoutinePreviewData.today.atTime(18, 30).atZone(zone).toInstant()
    val now: Instant = startedAt.plusSeconds(32 * 60 + 14)

    private fun set(
        id: String,
        type: SetType = SetType.NORMAL,
        done: Boolean = false,
        weightKg: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
    ) = WorkoutSet(
        id = id,
        setType = type,
        isCompleted = done,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        completedAt = if (done) startedAt else null,
    )

    private fun exercise(exercise: Exercise, vararg sets: WorkoutSet, note: String? = null) =
        WorkoutExercise(id = "we-${exercise.id}", exercise = exercise, sets = sets.toList(), note = note)

    val workout = Workout(
        id = "w",
        name = "Push",
        routineId = RoutinePreviewData.push.id,
        startedAt = startedAt,
        finishedAt = null,
        exercises = listOf(
            exercise(
                RoutinePreviewData.bench,
                set("b0", SetType.WARMUP, done = true, weightKg = 40.0, reps = 10),
                set("b1", done = true, weightKg = 80.0, reps = 8),
                set("b2", done = true, weightKg = 80.0, reps = 8),
                set("b3", weightKg = 80.0, reps = 7),
                set("b4", SetType.FAILURE, weightKg = 70.0, reps = 10),
            ),
            exercise(
                RoutinePreviewData.overheadPress,
                set("o1", weightKg = 47.5, reps = 8),
                set("o2", weightKg = 47.5, reps = 8),
                set("o3", weightKg = 47.5, reps = 8),
                note = "Strict — no leg drive.",
            ),
            exercise(
                RoutinePreviewData.dips,
                set("d1", reps = 12),
                set("d2", reps = 12),
            ),
            exercise(
                RoutinePreviewData.rowing,
                set("r1", durationSeconds = 600, distanceMeters = 2_000.0),
            ),
        ),
        rest = RestTimer(startedAt = now.minusSeconds(37), endsAt = now.plusSeconds(53)),
    )

    /** The screen's state for [workout]. */
    fun state(
        workout: Workout = this.workout,
        unit: WeightUnit = WeightUnit.KG,
        isReordering: Boolean = false,
        workoutNote: String = "",
    ) = ActiveWorkoutUiState(
        isLoading = false,
        workout = workout,
        exercises = workout.exercises.map { exercise ->
            var working = 0
            ActiveExercise(
                item = exercise,
                sets = exercise.sets.map { set ->
                    ActiveSet(
                        set = set,
                        number = if (set.setType == SetType.WARMUP) null else ++working,
                        fields = SetFields.from(set.values, unit),
                    )
                },
                note = TextFieldState(exercise.note.orEmpty()),
                showNote = exercise.note != null,
                restSeconds = if (exercise.exercise.id == RoutinePreviewData.bench.id) 180 else 90,
                hasOwnRest = exercise.exercise.id == RoutinePreviewData.bench.id,
            )
        },
        workoutNote = TextFieldState(workoutNote),
        progress = workout.progress(),
        rest = workout.rest,
        weightUnit = unit,
        zone = zone,
        isReordering = isReordering,
    )
}
