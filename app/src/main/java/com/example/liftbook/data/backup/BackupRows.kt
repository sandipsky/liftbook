package com.example.liftbook.data.backup

import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.data.local.entity.RoutineExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutEntity
import com.example.liftbook.data.local.entity.WorkoutExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.data.preferences.enumOrNull
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import java.time.Instant
import java.util.Locale

/** A backup as database rows: what an export reads, and what an import writes. */
internal data class BackupRows(
    val exercises: List<ExerciseEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineExercises: List<RoutineExerciseEntity> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val workoutExercises: List<WorkoutExerciseEntity> = emptyList(),
    val sets: List<WorkoutSetEntity> = emptyList(),
    val bodyWeight: List<BodyWeightEntryEntity> = emptyList(),
) {
    fun counts(): DataCounts = DataCounts(
        workouts = workouts.size,
        routines = routines.size,
        customExercises = exercises.count { it.isCustom },
        weighIns = bodyWeight.size,
    )
}

/** The file for these rows. Children are nested under their parents, in their on-screen order. */
internal fun BackupRows.toBackupFile(exportedAt: Instant, appVersion: String?, preferences: UserPreferences): BackupFile {
    val targetsByRoutine = routineExercises.groupBy { it.routineId }
    val exercisesByWorkout = workoutExercises.groupBy { it.workoutId }
    val setsByExercise = sets.groupBy { it.workoutExerciseId }
    return BackupFile(
        exportedAt = exportedAt,
        appVersion = appVersion,
        settings = BackupSettings(
            defaultRestSeconds = preferences.defaultRestSeconds,
            firstDayOfWeek = preferences.firstDayOfWeek.name,
            themeMode = preferences.themeMode.name,
        ),
        exercises = exercises.map { it.toBackup() },
        routines = routines.map { routine ->
            routine.toBackup(targetsByRoutine[routine.id].orEmpty().sortedBy { it.position }.map { it.toBackup() })
        },
        workouts = workouts.filter { it.finishedAt != null }.map { workout ->
            workout.toBackup(
                exercisesByWorkout[workout.id].orEmpty().sortedBy { it.position }.map { exercise ->
                    exercise.toBackup(setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.map { it.toBackup() })
                },
            )
        },
        bodyWeight = bodyWeight.map { it.toBackup() },
    )
}

/**
 * The rows for this file. Positions come from list order, and each set's workout and exercise
 * from the workout-exercise it's under, never from the file. A workout keeps its routine only
 * if the file has it, as deleting a routine unlinks its workouts.
 */
internal fun BackupFile.toRows(): BackupRows {
    val routineIds = routines.mapTo(HashSet()) { it.id }
    return BackupRows(
        exercises = exercises.map { it.toEntity() },
        routines = routines.map { it.toEntity() },
        routineExercises = routines.flatMap { routine ->
            routine.exercises.mapIndexed { position, target -> target.toEntity(routine.id, position) }
        },
        workouts = workouts.map { it.toEntity().copy(routineId = it.routineId?.takeIf(routineIds::contains)) },
        workoutExercises = workouts.flatMap { workout ->
            workout.exercises.mapIndexed { position, exercise -> exercise.toEntity(workout.id, position) }
        },
        sets = workouts.flatMap { workout ->
            workout.exercises.flatMap { exercise ->
                exercise.sets.mapIndexed { position, set -> set.toEntity(workout.id, exercise, position) }
            }
        },
        bodyWeight = bodyWeight.map { it.toEntity() },
    )
}

/**
 * The settings a backup restores (FR-6.4, replace). Anything missing or unknown takes its
 * default, as on a fresh install — a replace makes this phone the backup, not a blend of both.
 */
internal fun BackupSettings.toUserPreferences(locale: Locale = Locale.getDefault()): UserPreferences = UserPreferences(
    defaultRestSeconds = defaultRestSeconds?.takeIf { it >= 0 } ?: UserPreferences.DEFAULT_REST_SECONDS,
    firstDayOfWeek = enumOrNull<FirstDayOfWeek>(firstDayOfWeek) ?: FirstDayOfWeek.defaultFor(locale),
    themeMode = enumOrNull<ThemeMode>(themeMode) ?: ThemeMode.SYSTEM,
)

private fun ExerciseEntity.toBackup() = BackupExercise(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    equipment = equipment,
    type = type,
    isCustom = isCustom,
    isArchived = isArchived,
    defaultRestSeconds = defaultRestSeconds,
    notes = notes,
    createdAt = createdAt,
)

private fun BackupExercise.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    equipment = equipment,
    type = type,
    isCustom = isCustom,
    isArchived = isArchived,
    defaultRestSeconds = defaultRestSeconds,
    notes = notes,
    createdAt = createdAt,
)

private fun RoutineEntity.toBackup(exercises: List<BackupRoutineExercise>) = BackupRoutine(
    id = id,
    name = name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    exercises = exercises,
)

private fun BackupRoutine.toEntity() = RoutineEntity(id = id, name = name, notes = notes, createdAt = createdAt, updatedAt = updatedAt)

private fun RoutineExerciseEntity.toBackup() = BackupRoutineExercise(
    id = id,
    exerciseId = exerciseId,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg,
    targetDurationSeconds = targetDurationSeconds,
    targetDistanceMeters = targetDistanceMeters,
    restSecondsOverride = restSecondsOverride,
    notes = notes,
)

private fun BackupRoutineExercise.toEntity(routineId: String, position: Int) = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    position = position,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg,
    targetDurationSeconds = targetDurationSeconds,
    targetDistanceMeters = targetDistanceMeters,
    restSecondsOverride = restSecondsOverride,
    notes = notes,
)

private fun WorkoutEntity.toBackup(exercises: List<BackupWorkoutExercise>) = BackupWorkout(
    id = id,
    name = name,
    routineId = routineId,
    startedAt = startedAt,
    finishedAt = checkNotNull(finishedAt) { "Only finished workouts are backed up" },
    note = note,
    exercises = exercises,
)

// A finished workout has no rest running, so the rest timer's columns stay empty.
private fun BackupWorkout.toEntity() = WorkoutEntity(
    id = id,
    name = name,
    routineId = routineId,
    startedAt = startedAt,
    finishedAt = finishedAt,
    note = note,
)

private fun WorkoutExerciseEntity.toBackup(sets: List<BackupSet>) = BackupWorkoutExercise(
    id = id,
    exerciseId = exerciseId,
    note = note,
    restSecondsOverride = restSecondsOverride,
    sets = sets,
)

private fun BackupWorkoutExercise.toEntity(workoutId: String, position: Int) = WorkoutExerciseEntity(
    id = id,
    workoutId = workoutId,
    exerciseId = exerciseId,
    position = position,
    note = note,
    restSecondsOverride = restSecondsOverride,
)

private fun WorkoutSetEntity.toBackup() = BackupSet(
    id = id,
    setType = setType,
    isCompleted = isCompleted,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    completedAt = completedAt,
)

private fun BackupSet.toEntity(workoutId: String, exercise: BackupWorkoutExercise, position: Int) = WorkoutSetEntity(
    id = id,
    workoutExerciseId = exercise.id,
    workoutId = workoutId,
    exerciseId = exercise.exerciseId,
    position = position,
    setType = setType,
    isCompleted = isCompleted,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    completedAt = completedAt,
)

private fun BodyWeightEntryEntity.toBackup() = BackupWeighIn(id = id, weightKg = weightKg, date = recordedOn, note = note)

private fun BackupWeighIn.toEntity() = BodyWeightEntryEntity(id = id, weightKg = weightKg, recordedOn = date, note = note)
