package com.example.liftbook.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.CHEST -> R.string.muscle_chest
    MuscleGroup.BACK -> R.string.muscle_back
    MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
    MuscleGroup.BICEPS -> R.string.muscle_biceps
    MuscleGroup.TRICEPS -> R.string.muscle_triceps
    MuscleGroup.FOREARMS -> R.string.muscle_forearms
    MuscleGroup.CORE -> R.string.muscle_core
    MuscleGroup.QUADS -> R.string.muscle_quads
    MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
    MuscleGroup.GLUTES -> R.string.muscle_glutes
    MuscleGroup.CALVES -> R.string.muscle_calves
    MuscleGroup.FULL_BODY -> R.string.muscle_full_body
    MuscleGroup.CARDIO -> R.string.muscle_cardio
    MuscleGroup.OTHER -> R.string.muscle_other
}

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.BARBELL -> R.string.equipment_barbell
    Equipment.DUMBBELL -> R.string.equipment_dumbbell
    Equipment.KETTLEBELL -> R.string.equipment_kettlebell
    Equipment.CABLE -> R.string.equipment_cable
    Equipment.MACHINE -> R.string.equipment_machine
    Equipment.BAND -> R.string.equipment_band
    Equipment.NONE -> R.string.equipment_none
    Equipment.CARDIO_MACHINE -> R.string.equipment_cardio_machine
    Equipment.OTHER -> R.string.equipment_other
}

/**
 * Types are named for what a set records rather than as categories. That is the only thing the
 * type changes (FR-3.3), and it keeps timed holds like Plank from reading as "cardio".
 */
@StringRes
fun ExerciseType.labelRes(): Int = when (this) {
    ExerciseType.STRENGTH -> R.string.exercise_type_strength
    ExerciseType.BODYWEIGHT -> R.string.exercise_type_bodyweight
    ExerciseType.CARDIO -> R.string.exercise_type_cardio
}

@StringRes
fun ExerciseType.examplesRes(): Int = when (this) {
    ExerciseType.STRENGTH -> R.string.exercise_type_strength_examples
    ExerciseType.BODYWEIGHT -> R.string.exercise_type_bodyweight_examples
    ExerciseType.CARDIO -> R.string.exercise_type_cardio_examples
}

/** The order types are offered in: most common first. */
val ExerciseTypeDisplayOrder = listOf(ExerciseType.STRENGTH, ExerciseType.BODYWEIGHT, ExerciseType.CARDIO)

/** "Chest · Barbell", or "Chest · Cable · Custom" for the user's own exercises. */
@Composable
fun exerciseMetaText(exercise: Exercise): String {
    val muscle = stringResource(exercise.primaryMuscle.labelRes())
    val equipment = stringResource(exercise.equipment.labelRes())
    return if (exercise.isCustom) {
        stringResource(R.string.exercise_meta_custom, muscle, equipment)
    } else {
        stringResource(R.string.exercise_meta, muscle, equipment)
    }
}

/** [exerciseMetaText] without the visual separators, for screen readers. */
@Composable
fun exerciseMetaSpoken(exercise: Exercise): String {
    val muscle = stringResource(exercise.primaryMuscle.labelRes())
    val equipment = stringResource(exercise.equipment.labelRes())
    return if (exercise.isCustom) {
        stringResource(R.string.exercise_meta_custom_spoken, muscle, equipment)
    } else {
        stringResource(R.string.exercise_meta_spoken, muscle, equipment)
    }
}
