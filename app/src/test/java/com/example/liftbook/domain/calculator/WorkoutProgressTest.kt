package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.testing.exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class WorkoutProgressTest {

    private val bench = exercise("Bench Press (Barbell)")
    private val squat = exercise("Squat (Barbell)", muscle = MuscleGroup.QUADS)
    private val start = Instant.parse("2026-09-25T18:30:00Z")

    private fun set(id: String, done: Boolean, weightKg: Double = 80.0, reps: Int = 8, type: SetType = SetType.NORMAL) =
        WorkoutSet(id, type, isCompleted = done, weightKg = weightKg, reps = reps)

    private fun workout(vararg exercises: WorkoutExercise, finishedAt: Instant? = null) =
        Workout(id = "w", name = "Push", routineId = null, startedAt = start, finishedAt = finishedAt, exercises = exercises.toList())

    @Test
    fun `progress counts every set done, and the volume of the working ones`() {
        val progress = workout(
            WorkoutExercise("a", bench, listOf(set("1", done = true, type = SetType.WARMUP, weightKg = 40.0), set("2", done = true), set("3", done = false))),
            WorkoutExercise("b", squat, listOf(set("4", done = true, weightKg = 100.0, reps = 5))),
        ).progress()

        assertEquals(WorkoutProgress(completedSets = 3, totalSets = 4, volumeKg = 80.0 * 8 + 100.0 * 5), progress)
        assertEquals(1, progress.incompleteSets)
    }

    @Test
    fun `the next set is the next one not done, across exercises, then any skipped earlier`() {
        val workout = workout(
            WorkoutExercise("a", bench, listOf(set("1", done = false), set("2", done = true), set("3", done = true))),
            WorkoutExercise("b", squat, listOf(set("4", done = true), set("5", done = false))),
        )

        assertEquals("5", workout.nextSetAfter("2")?.set?.id)
        assertEquals(squat, workout.nextSetAfter("3")?.exercise?.exercise)
        // Nothing after set 5 is left, so it's the one skipped at the start.
        assertEquals("1", workout.nextSetAfter("5")?.set?.id)
    }

    @Test
    fun `when every other set is done there's no next set`() {
        val workout = workout(WorkoutExercise("a", bench, listOf(set("1", done = true), set("2", done = false))))

        assertNull(workout.nextSetAfter("2"))
    }

    @Test
    fun `a summary adds up the workout and finds its records`() {
        val finished = workout(
            WorkoutExercise("a", bench, listOf(set("1", done = true, weightKg = 100.0, reps = 5))),
            WorkoutExercise("b", squat, listOf(set("2", done = true, weightKg = 120.0, reps = 5))),
            finishedAt = start.plusSeconds(3_725),
        )
        // Bench has history and beat it; squat is done for the first time, so has no records.
        val previous = mapOf(bench.id to listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(95.0, 5))))

        val summary = finished.summarize(previous)

        assertEquals(3_725L, summary.durationSeconds)
        assertEquals(100.0 * 5 + 120.0 * 5, summary.volumeKg, 1e-9)
        assertEquals(2, summary.completedSets)
        assertEquals(listOf(bench), summary.records.map { it.exercise })
        assertEquals(PersonalRecord.HeaviestWeight(100.0, 5), summary.records.single().records.first())
    }

    @Test
    fun `an exercise done twice in a workout is judged once, on all its sets`() {
        val finished = workout(
            WorkoutExercise("a", bench, listOf(set("1", done = true, weightKg = 100.0, reps = 5))),
            WorkoutExercise("b", bench, listOf(set("2", done = true, weightKg = 105.0, reps = 3))),
            finishedAt = start.plusSeconds(60),
        )
        val previous = mapOf(bench.id to listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(95.0, 5))))

        val records = finished.summarize(previous).records

        assertEquals(1, records.size)
        assertEquals(PersonalRecord.HeaviestWeight(105.0, 3), records.single().records.first())
    }

    @Test
    fun `cardio adds nothing to volume but its sets count`() {
        val rowing = exercise("Rowing (Machine)", type = ExerciseType.CARDIO)
        val summary = workout(
            WorkoutExercise("a", rowing, listOf(WorkoutSet("1", SetType.NORMAL, isCompleted = true, durationSeconds = 600))),
            finishedAt = start.plusSeconds(600),
        ).summarize(emptyMap())

        assertEquals(0.0, summary.volumeKg, 0.0)
        assertEquals(1, summary.completedSets)
    }

    @Test
    fun `an empty workout is named for the part of the day it starts in`() {
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(LocalTime.of(4, 59)))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.of(LocalTime.of(5, 0)))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.of(LocalTime.of(11, 59)))
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDay.of(LocalTime.of(12, 0)))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.of(LocalTime.of(17, 0)))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.of(LocalTime.of(21, 59)))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(LocalTime.of(22, 0)))
    }
}
