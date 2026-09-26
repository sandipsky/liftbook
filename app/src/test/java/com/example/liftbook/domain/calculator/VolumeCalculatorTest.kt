package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.testing.exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeCalculatorTest {

    private fun strength(weightKg: Double, reps: Int, type: SetType = SetType.NORMAL) =
        LoggedSet(type, SetMetrics.Strength(weightKg, reps))

    @Test
    fun `volume is weight times reps, summed`() {
        assertEquals(80.0 * 8 + 82.5 * 6, volume(listOf(strength(80.0, 8), strength(82.5, 6))), 1e-9)
    }

    @Test
    fun `warm-ups don't count towards volume`() {
        val sets = listOf(strength(40.0, 10, SetType.WARMUP), strength(80.0, 8), strength(60.0, 12, SetType.DROP), strength(80.0, 5, SetType.FAILURE))

        assertEquals(80.0 * 8 + 60.0 * 12 + 80.0 * 5, volume(sets), 1e-9)
    }

    @Test
    fun `a bodyweight set counts only what was added, and cardio counts nothing`() {
        val sets = listOf(
            LoggedSet(SetType.NORMAL, SetMetrics.Bodyweight(reps = 12, addedWeightKg = null)),
            LoggedSet(SetType.NORMAL, SetMetrics.Bodyweight(reps = 8, addedWeightKg = 10.0)),
            LoggedSet(SetType.NORMAL, SetMetrics.Cardio(durationSeconds = 600, distanceMeters = 2_000.0)),
        )

        assertEquals(80.0, volume(sets), 1e-9)
    }

    @Test
    fun `no sets is no volume`() {
        assertEquals(0.0, volume(emptyList()), 0.0)
    }

    @Test
    fun `completed sets leave out sets not done, and done sets missing a value`() {
        val bench = WorkoutExercise(
            id = "we",
            exercise = exercise("Bench Press (Barbell)", type = ExerciseType.STRENGTH),
            sets = listOf(
                WorkoutSet("1", SetType.WARMUP, isCompleted = true, weightKg = 40.0, reps = 10),
                WorkoutSet("2", SetType.NORMAL, isCompleted = true, weightKg = 80.0, reps = 8),
                WorkoutSet("3", SetType.NORMAL, isCompleted = false, weightKg = 80.0, reps = 8),
                WorkoutSet("4", SetType.NORMAL, isCompleted = true, weightKg = null, reps = 8),
            ),
        )

        assertEquals(listOf(strength(40.0, 10, SetType.WARMUP), strength(80.0, 8)), bench.completedSets())
    }
}
