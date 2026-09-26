package com.example.liftbook.ui.feature.routines

import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.RoutineExercise
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.WeightUnit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Sample data for the routine, home and workout previews. */
internal object RoutinePreviewData {

    val zone: ZoneId = ZoneId.of("UTC")
    val today: LocalDate = LocalDate.of(2026, 9, 25)

    private val created = today.minusDays(40).atStartOfDay(zone).toInstant()

    private fun exercise(
        id: String,
        name: String,
        muscle: MuscleGroup,
        equipment: Equipment,
        type: ExerciseType = ExerciseType.STRENGTH,
    ) = Exercise(id, name, muscle, equipment, type, isCustom = false, isArchived = false, createdAt = created)

    val bench = exercise("bench", "Bench Press (Barbell)", MuscleGroup.CHEST, Equipment.BARBELL)
    val overheadPress = exercise("ohp", "Overhead Press (Barbell)", MuscleGroup.SHOULDERS, Equipment.BARBELL)
    val inclineDumbbell = exercise("incline-db", "Incline Bench Press (Dumbbell)", MuscleGroup.CHEST, Equipment.DUMBBELL)
    val dips = exercise("dips", "Chest Dip", MuscleGroup.CHEST, Equipment.NONE, ExerciseType.BODYWEIGHT)
    val pushdown = exercise("pushdown", "Triceps Pushdown (Cable)", MuscleGroup.TRICEPS, Equipment.CABLE)
    val rowing = exercise("rowing", "Rowing (Machine)", MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE, ExerciseType.CARDIO)
    val pullUp = exercise("pull-up", "Pull-Up", MuscleGroup.BACK, Equipment.NONE, ExerciseType.BODYWEIGHT)
    val row = exercise("row", "Bent-Over Row (Barbell)", MuscleGroup.BACK, Equipment.BARBELL)
    val squat = exercise("squat", "Squat (Barbell)", MuscleGroup.QUADS, Equipment.BARBELL)
    val rdl = exercise("rdl", "Romanian Deadlift (Barbell)", MuscleGroup.HAMSTRINGS, Equipment.BARBELL)
    val plank = exercise("plank", "Plank", MuscleGroup.CORE, Equipment.NONE, ExerciseType.CARDIO)

    private fun item(exercise: Exercise, target: SetTarget) = RoutineExercise(id = "re-${exercise.id}", exercise = exercise, target = target)

    private fun routine(id: String, name: String, lastDoneDaysAgo: Long?, vararg exercises: RoutineExercise) = Routine(
        id = id,
        name = name,
        exercises = exercises.toList(),
        lastPerformedAt = lastDoneDaysAgo?.let { today.minusDays(it).atTime(LocalTime.of(18, 30)).atZone(zone).toInstant() },
        createdAt = created,
        updatedAt = created,
    )

    val push = routine(
        "push", "Push", 2,
        item(bench, SetTarget(sets = 4, reps = 6, weightKg = 80.0)),
        item(overheadPress, SetTarget(sets = 3, reps = 8, weightKg = 47.5)),
        item(inclineDumbbell, SetTarget(sets = 3, reps = 10, weightKg = 26.0)),
        item(dips, SetTarget(sets = 3, reps = 12)),
        item(pushdown, SetTarget(sets = 3, reps = 15)),
        item(rowing, SetTarget(sets = 1, durationSeconds = 600, distanceMeters = 2_000.0)),
    )

    val pull = routine(
        "pull", "Pull", 5,
        item(pullUp, SetTarget(sets = 4, reps = 8)),
        item(row, SetTarget(sets = 4, reps = 8, weightKg = 70.0)),
    )

    val legs = routine(
        "legs", "Legs", null,
        item(squat, SetTarget(sets = 5, reps = 5, weightKg = 110.0)),
        item(rdl, SetTarget(sets = 3, reps = 8, weightKg = 90.0)),
        item(plank, SetTarget(sets = 3, durationSeconds = 45)),
    )

    val routines = listOf(legs, pull, push)

    /** The editor with [push] loaded. */
    fun editorState(unit: WeightUnit = WeightUnit.KG) = RoutineEditorUiState(
        isEditing = true,
        exercises = push.exercises.take(4).map {
            RoutineEditorExercise(key = it.id, exercise = it.exercise, fields = TargetFields.from(it.target, unit))
        } + RoutineEditorExercise(
            key = "rowing",
            exercise = rowing,
            fields = TargetFields.from(push.exercises.last().target, unit),
        ),
        weightUnit = unit,
        hasUnsavedChanges = true,
    )
}
