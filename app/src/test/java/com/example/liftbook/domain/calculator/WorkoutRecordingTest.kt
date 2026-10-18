package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutMode
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.testing.exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class WorkoutRecordingTest {

    private val bench = exercise("Bench Press (Barbell)")
    private val rowing = exercise("Rowing (Machine)", type = ExerciseType.CARDIO)

    private fun workout(mode: WorkoutMode, vararg exercises: WorkoutExercise) = Workout(
        id = "w",
        name = "Push",
        routineId = null,
        startedAt = Instant.EPOCH,
        finishedAt = null,
        exercises = exercises.toList(),
        mode = mode,
    )

    @Test
    fun `in a recorded workout a set is done once it holds what its type records`() {
        val recorded = workout(
            WorkoutMode.RECORDED,
            WorkoutExercise(
                "a",
                bench,
                listOf(
                    WorkoutSet("1", SetType.NORMAL, isCompleted = false, weightKg = 80.0, reps = 8),
                    WorkoutSet("2", SetType.WARMUP, isCompleted = false, weightKg = 40.0, reps = 10),
                    // Reps missing: not filled in.
                    WorkoutSet("3", SetType.NORMAL, isCompleted = false, weightKg = 80.0),
                    WorkoutSet("4", SetType.NORMAL, isCompleted = false),
                ),
            ),
            WorkoutExercise(
                "b",
                rowing,
                listOf(
                    WorkoutSet("5", SetType.NORMAL, isCompleted = false, durationSeconds = 600),
                    // Distance alone isn't a cardio set.
                    WorkoutSet("6", SetType.NORMAL, isCompleted = false, distanceMeters = 2_000.0),
                ),
            ),
        )

        val read = recorded.withRecordedSets()

        assertEquals(listOf(true, true, false, false), read.exercises[0].sets.map { it.isCompleted })
        assertEquals(listOf(true, false), read.exercises[1].sets.map { it.isCompleted })
        // So progress and volume read the filled-in sets, warm-ups excluded from volume as ever.
        assertEquals(WorkoutProgress(completedSets = 3, totalSets = 6, volumeKg = 80.0 * 8), read.progress())
    }

    @Test
    fun `a live workout is left as it is, done meaning marked done`() {
        val live = workout(
            WorkoutMode.LIVE,
            WorkoutExercise("a", bench, listOf(WorkoutSet("1", SetType.NORMAL, isCompleted = false, weightKg = 80.0, reps = 8))),
        )

        assertSame(live, live.withRecordedSets())
    }
}
