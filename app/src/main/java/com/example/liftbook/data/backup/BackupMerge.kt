package com.example.liftbook.data.backup

import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.domain.calculator.ExerciseNames
import com.example.liftbook.domain.calculator.RoutineNames
import java.util.Locale

/** What's on this phone already, as far as a merge needs to know. */
internal data class LocalRows(
    val exercises: List<ExerciseEntity>,
    val routines: List<RoutineEntity>,
    /** Every workout, the one in progress included. */
    val workoutIds: Set<String>,
    val bodyWeight: List<BodyWeightEntryEntity>,
)

/**
 * The rows a merge adds (FR-6.4). Ids are UUIDs, so a merge is a union by id: anything this phone
 * already has is left exactly as it is, and only what's missing is added. Beyond ids:
 *
 * - An exercise new to this phone but named like one in its library (ignoring case) is the same
 *   lift, when it records the same way — its sets join the one here rather than splitting the
 *   history across two exercises that look alike. One named like an exercise of another type is
 *   added with a number ("Zercher Squat 2"), since library names are unique.
 * - A new routine named like one here is numbered the same way.
 * - A weigh-in on a day this phone already has one for is skipped: one a day, and this phone's wins.
 * - A workout keeps its routine only if that routine is here or arriving with it.
 */
internal fun planMerge(backup: BackupRows, local: LocalRows): BackupRows {
    val localExerciseIds = local.exercises.mapTo(HashSet()) { it.id }
    // Library names are unique among exercises that aren't archived; archived ones free theirs.
    val library = local.exercises.filterNot { it.isArchived }.associateByTo(HashMap()) { it.name.nameKey() }
    val libraryNames = library.values.mapTo(ArrayList()) { it.name }

    val exerciseIds = HashMap<String, String>()
    val newExercises = ArrayList<ExerciseEntity>()
    for (exercise in backup.exercises) {
        if (exercise.id in localExerciseIds) {
            exerciseIds[exercise.id] = exercise.id
            continue
        }
        val namesake = library[exercise.name.nameKey()]
        if (namesake != null && namesake.type == exercise.type) {
            exerciseIds[exercise.id] = namesake.id
            continue
        }
        val name = if (namesake != null && !exercise.isArchived) ExerciseNames.copyName(exercise.name, libraryNames) else exercise.name
        val added = exercise.copy(name = name)
        newExercises += added
        exerciseIds[exercise.id] = exercise.id
        if (!added.isArchived) {
            library[name.nameKey()] = added
            libraryNames += name
        }
    }

    val localRoutineIds = local.routines.mapTo(HashSet()) { it.id }
    val routineNames = local.routines.mapTo(ArrayList()) { it.name }
    val newRoutines = backup.routines
        .filterNot { it.id in localRoutineIds }
        .map { routine ->
            val clashes = routineNames.any { it.nameKey() == routine.name.nameKey() }
            val name = if (clashes) RoutineNames.copyName(routine.name, routineNames) else routine.name
            routineNames += name
            routine.copy(name = name)
        }
    val newRoutineIds = newRoutines.mapTo(HashSet()) { it.id }
    val knownRoutineIds = localRoutineIds + newRoutineIds

    val newWorkouts = backup.workouts
        .filterNot { it.id in local.workoutIds }
        .map { workout -> workout.copy(routineId = workout.routineId?.takeIf { it in knownRoutineIds }) }
    val newWorkoutIds = newWorkouts.mapTo(HashSet()) { it.id }

    val localDays = local.bodyWeight.mapTo(HashSet()) { it.recordedOn }
    val localWeighInIds = local.bodyWeight.mapTo(HashSet()) { it.id }

    return BackupRows(
        exercises = newExercises,
        routines = newRoutines,
        routineExercises = backup.routineExercises
            .filter { it.routineId in newRoutineIds }
            .map { it.copy(exerciseId = exerciseIds.getValue(it.exerciseId)) },
        workouts = newWorkouts,
        workoutExercises = backup.workoutExercises
            .filter { it.workoutId in newWorkoutIds }
            .map { it.copy(exerciseId = exerciseIds.getValue(it.exerciseId)) },
        sets = backup.sets
            .filter { it.workoutId in newWorkoutIds }
            .map { it.copy(exerciseId = exerciseIds.getValue(it.exerciseId)) },
        bodyWeight = backup.bodyWeight.filterNot { it.id in localWeighInIds || it.recordedOn in localDays },
    )
}

private fun String.nameKey(): String = ExerciseNames.normalize(this).lowercase(Locale.ROOT)
