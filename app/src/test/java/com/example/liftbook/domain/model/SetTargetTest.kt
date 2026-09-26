package com.example.liftbook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SetTargetTest {

    private val everything = SetTarget(sets = 3, reps = 8, weightKg = 80.0, durationSeconds = 60, distanceMeters = 1_000.0)

    @Test
    fun `strength keeps weight and reps`() {
        assertEquals(SetTarget(sets = 3, reps = 8, weightKg = 80.0), everything.applicableTo(ExerciseType.STRENGTH))
    }

    @Test
    fun `bodyweight keeps reps only`() {
        assertEquals(SetTarget(sets = 3, reps = 8), everything.applicableTo(ExerciseType.BODYWEIGHT))
    }

    @Test
    fun `cardio keeps time and distance`() {
        assertEquals(
            SetTarget(sets = 3, durationSeconds = 60, distanceMeters = 1_000.0),
            everything.applicableTo(ExerciseType.CARDIO),
        )
    }

    @Test
    fun `new exercises start at 3 by 10, or one set of cardio`() {
        assertEquals(SetTarget(sets = 3, reps = 10), SetTarget.defaultFor(ExerciseType.STRENGTH))
        assertEquals(SetTarget(sets = 3, reps = 10), SetTarget.defaultFor(ExerciseType.BODYWEIGHT))
        assertEquals(SetTarget(sets = 1), SetTarget.defaultFor(ExerciseType.CARDIO))
    }
}
