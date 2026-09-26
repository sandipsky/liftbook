package com.example.liftbook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetValuesTest {

    @Test
    fun `a strength set needs a weight and at least one rep`() {
        assertEquals(SetMetrics.Strength(80.0, 8), SetValues(weightKg = 80.0, reps = 8).metricsFor(ExerciseType.STRENGTH))
        assertNull(SetValues(reps = 8).metricsFor(ExerciseType.STRENGTH))
        assertNull(SetValues(weightKg = 80.0).metricsFor(ExerciseType.STRENGTH))
        assertNull(SetValues(weightKg = 80.0, reps = 0).metricsFor(ExerciseType.STRENGTH))
    }

    @Test
    fun `a strength set may weigh nothing — an empty machine, say`() {
        assertEquals(SetMetrics.Strength(0.0, 12), SetValues(weightKg = 0.0, reps = 12).metricsFor(ExerciseType.STRENGTH))
    }

    @Test
    fun `a bodyweight set needs reps, and any weight is added weight`() {
        assertEquals(SetMetrics.Bodyweight(12, null), SetValues(reps = 12).metricsFor(ExerciseType.BODYWEIGHT))
        assertEquals(SetMetrics.Bodyweight(8, 10.0), SetValues(weightKg = 10.0, reps = 8).metricsFor(ExerciseType.BODYWEIGHT))
        assertNull(SetValues(weightKg = 10.0).metricsFor(ExerciseType.BODYWEIGHT))
    }

    @Test
    fun `a cardio set needs a duration, and distance is optional`() {
        assertEquals(SetMetrics.Cardio(600, null), SetValues(durationSeconds = 600).metricsFor(ExerciseType.CARDIO))
        assertEquals(SetMetrics.Cardio(600, 2_000.0), SetValues(durationSeconds = 600, distanceMeters = 2_000.0).metricsFor(ExerciseType.CARDIO))
        assertNull(SetValues(distanceMeters = 2_000.0).metricsFor(ExerciseType.CARDIO))
    }

    @Test
    fun `only the values a type records apply to it`() {
        val all = SetValues(weightKg = 80.0, reps = 8, durationSeconds = 60, distanceMeters = 500.0)

        assertEquals(SetValues(weightKg = 80.0, reps = 8), all.applicableTo(ExerciseType.STRENGTH))
        assertEquals(SetValues(reps = 8), all.applicableTo(ExerciseType.BODYWEIGHT))
        assertEquals(SetValues(durationSeconds = 60, distanceMeters = 500.0), all.applicableTo(ExerciseType.CARDIO))
        assertTrue(SetValues(durationSeconds = 60).isBlankFor(ExerciseType.STRENGTH))
        assertFalse(SetValues(reps = 1).isBlankFor(ExerciseType.STRENGTH))
    }
}
