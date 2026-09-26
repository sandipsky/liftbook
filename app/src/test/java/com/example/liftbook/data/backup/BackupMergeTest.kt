package com.example.liftbook.data.backup

import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Merging a backup into a phone that has data of its own (FR-6.4). */
class BackupMergeTest {

    private val created = Instant.parse("2026-01-01T00:00:00Z")

    private fun exercise(id: String, name: String, type: ExerciseType = ExerciseType.STRENGTH, isCustom: Boolean = true, isArchived: Boolean = false) =
        BackupExercise(
            id = id,
            name = name,
            primaryMuscle = MuscleGroup.QUADS,
            equipment = Equipment.BARBELL,
            type = type,
            isCustom = isCustom,
            isArchived = isArchived,
            createdAt = created,
        )

    private fun BackupExercise.toLocal() = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscle = primaryMuscle,
        equipment = equipment,
        type = type,
        isCustom = isCustom,
        isArchived = isArchived,
        defaultRestSeconds = null,
        notes = null,
        createdAt = createdAt,
    )

    private fun workout(id: String, exerciseId: String, routineId: String? = null) = BackupWorkout(
        id = id,
        name = "Workout $id",
        routineId = routineId,
        startedAt = Instant.parse("2026-09-20T18:00:00Z"),
        finishedAt = Instant.parse("2026-09-20T19:00:00Z"),
        exercises = listOf(BackupWorkoutExercise(id = "$id-e", exerciseId = exerciseId, sets = listOf(BackupSet(id = "$id-s", weightKg = 100.0, reps = 5)))),
    )

    private fun routine(id: String, name: String, exerciseId: String) = BackupRoutine(
        id = id,
        name = name,
        createdAt = created,
        updatedAt = created,
        exercises = listOf(BackupRoutineExercise(id = "$id-e", exerciseId = exerciseId, targetSets = 3)),
    )

    private fun backup(
        exercises: List<BackupExercise>,
        routines: List<BackupRoutine> = emptyList(),
        workouts: List<BackupWorkout> = emptyList(),
        bodyWeight: List<BackupWeighIn> = emptyList(),
        schedules: List<BackupSchedule> = emptyList(),
    ) = BackupFile(
        exportedAt = created,
        exercises = exercises,
        routines = routines,
        workouts = workouts,
        bodyWeight = bodyWeight,
        schedules = schedules,
    ).toRows()

    private fun local(
        exercises: List<BackupExercise> = emptyList(),
        routines: List<RoutineEntity> = emptyList(),
        workoutIds: Set<String> = emptySet(),
        bodyWeight: List<BodyWeightEntryEntity> = emptyList(),
        schedules: List<BackupSchedule> = emptyList(),
    ) = LocalRows(exercises.map { it.toLocal() }, routines, workoutIds, bodyWeight, backup(emptyList(), schedules = schedules).schedules)

    private fun schedule(id: String, vararg days: String, at: LocalTime = LocalTime.of(18, 0), routineId: String? = null) =
        BackupSchedule(id = id, days = days.toList(), startTime = at, routineId = routineId, createdAt = created)

    private val squat = exercise("squat", "Squat (Barbell)", isCustom = false)

    @Test
    fun `what this phone has is left alone, and only what's missing is added`() {
        val added = planMerge(
            backup(
                exercises = listOf(squat),
                routines = listOf(routine("legs", "Legs", "squat"), routine("heavy", "Heavy", "squat")),
                workouts = listOf(workout("old", "squat"), workout("new", "squat")),
            ),
            local(
                exercises = listOf(squat),
                routines = listOf(RoutineEntity("legs", "Legs", null, created, created)),
                workoutIds = setOf("old"),
            ),
        )

        assertTrue(added.exercises.isEmpty())
        assertEquals(listOf("heavy"), added.routines.map { it.id })
        assertEquals(listOf("heavy"), added.routineExercises.map { it.routineId })
        assertEquals(listOf("new"), added.workouts.map { it.id })
        assertEquals(listOf("new-e"), added.workoutExercises.map { it.id })
        assertEquals(listOf("new-s"), added.sets.map { it.id })
    }

    @Test
    fun `merging what's already here adds nothing`() {
        val rows = backup(exercises = listOf(squat), workouts = listOf(workout("w", "squat")))

        val added = planMerge(rows, local(exercises = listOf(squat), workoutIds = setOf("w")))

        assertEquals(BackupRows(), added)
    }

    @Test
    fun `an exercise named like one here, recording the same way, is that exercise`() {
        val here = exercise("mine", "Zercher Squat")
        val theirs = exercise("theirs", "zercher  squat")

        val added = planMerge(
            backup(exercises = listOf(theirs), routines = listOf(routine("r", "Legs", "theirs")), workouts = listOf(workout("w", "theirs"))),
            local(exercises = listOf(here)),
        )

        // One exercise, so its history isn't split across two that look alike.
        assertTrue(added.exercises.isEmpty())
        assertEquals(listOf("mine"), added.routineExercises.map { it.exerciseId })
        assertEquals(listOf("mine"), added.workoutExercises.map { it.exerciseId })
        assertEquals(listOf("mine"), added.sets.map { it.exerciseId })
    }

    @Test
    fun `one named like an exercise of another type is added, numbered`() {
        val here = exercise("mine", "Plank Hold", type = ExerciseType.CARDIO)
        val theirs = exercise("theirs", "Plank Hold", type = ExerciseType.BODYWEIGHT)

        val added = planMerge(backup(exercises = listOf(theirs), workouts = listOf(workout("w", "theirs"))), local(exercises = listOf(here)))

        assertEquals(listOf("Plank Hold 2"), added.exercises.map { it.name })
        assertEquals(listOf("theirs"), added.sets.map { it.exerciseId })
    }

    @Test
    fun `an archived exercise here doesn't claim its name`() {
        val archived = exercise("mine", "Zercher Squat", isArchived = true)
        val theirs = exercise("theirs", "Zercher Squat")

        val added = planMerge(backup(exercises = listOf(theirs)), local(exercises = listOf(archived)))

        assertEquals(listOf("theirs" to "Zercher Squat"), added.exercises.map { it.id to it.name })
    }

    @Test
    fun `a routine named like one here is numbered, as a copy is`() {
        val added = planMerge(
            backup(exercises = listOf(squat), routines = listOf(routine("theirs", "push", "squat"))),
            local(exercises = listOf(squat), routines = listOf(RoutineEntity("mine", "Push", null, created, created))),
        )

        assertEquals(listOf("push 2"), added.routines.map { it.name })
    }

    @Test
    fun `a weigh-in on a day this phone has one for is skipped`() {
        val day = LocalDate.of(2026, 9, 25)
        val added = planMerge(
            backup(
                exercises = emptyList(),
                bodyWeight = listOf(BackupWeighIn("theirs-25", 81.0, day), BackupWeighIn("theirs-26", 80.8, day.plusDays(1))),
            ),
            local(bodyWeight = listOf(BodyWeightEntryEntity("mine-25", 82.4, day, null))),
        )

        assertEquals(listOf("theirs-26"), added.bodyWeight.map { it.id })
    }

    @Test
    fun `a workout keeps its routine, whether that routine is already here or arriving with it`() {
        val added = planMerge(
            backup(
                exercises = listOf(squat),
                routines = listOf(routine("here", "Legs", "squat"), routine("arriving", "Heavy", "squat")),
                workouts = listOf(
                    workout("a", "squat", routineId = "here"),
                    workout("b", "squat", routineId = "arriving"),
                    // A routine the file doesn't have was deleted: its workouts are unlinked, as on the phone.
                    workout("c", "squat", routineId = "deleted"),
                ),
            ),
            local(exercises = listOf(squat), routines = listOf(RoutineEntity("here", "Legs", null, created, created))),
        )

        assertEquals(listOf("arriving"), added.routines.map { it.id })
        assertEquals(listOf("here", "arriving", null), added.workouts.map { it.routineId })
    }

    @Test
    fun `a scheduled workout is added, unless one here already reminds at that day and time`() {
        val added = planMerge(
            backup(
                exercises = listOf(squat),
                routines = listOf(routine("legs", "Legs", "squat")),
                schedules = listOf(
                    // Shares Monday at 18:00 with the entry here, so it would double that reminder.
                    schedule("clash", "MONDAY", "WEDNESDAY"),
                    // Same days, another time.
                    schedule("morning", "MONDAY", at = LocalTime.of(7, 0), routineId = "legs"),
                    // Another day at the same time, for a routine the file doesn't have.
                    schedule("tuesday", "TUESDAY", routineId = "deleted"),
                    // Already here.
                    schedule("here", "MONDAY"),
                ),
            ),
            local(exercises = listOf(squat), schedules = listOf(schedule("here", "MONDAY", "FRIDAY"))),
        )

        assertEquals(listOf("morning", "tuesday"), added.schedules.map { it.id })
        assertEquals(listOf("legs", null), added.schedules.map { it.routineId })
    }
}
