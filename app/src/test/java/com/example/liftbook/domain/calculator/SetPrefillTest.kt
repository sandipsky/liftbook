package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.calculator.SetPrefill.SetCandidate
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import org.junit.Assert.assertEquals
import org.junit.Test

class SetPrefillTest {

    private fun lift(weightKg: Double, reps: Int, type: SetType = SetType.NORMAL) = LoggedSet(type, SetMetrics.Strength(weightKg, reps))
    private fun values(weightKg: Double? = null, reps: Int? = null) = SetValues(weightKg = weightKg, reps = reps)

    @Test
    fun `an added exercise repeats last time's sets, types and all`() {
        val last = listOf(lift(40.0, 10, SetType.WARMUP), lift(80.0, 8), lift(80.0, 6, SetType.FAILURE))

        assertEquals(
            listOf(
                PlannedSet(SetType.WARMUP, values(40.0, 10)),
                PlannedSet(SetType.NORMAL, values(80.0, 8)),
                PlannedSet(SetType.FAILURE, values(80.0, 6)),
            ),
            SetPrefill.forAddedExercise(ExerciseType.STRENGTH, last),
        )
    }

    @Test
    fun `an exercise never done starts with empty sets`() {
        assertEquals(List(3) { PlannedSet() }, SetPrefill.forAddedExercise(ExerciseType.STRENGTH, emptyList()))
        assertEquals(listOf(PlannedSet()), SetPrefill.forAddedExercise(ExerciseType.CARDIO, emptyList()))
    }

    @Test
    fun `a bodyweight exercise carries reps only`() {
        val last = listOf(LoggedSet(SetType.NORMAL, SetMetrics.Bodyweight(reps = 10, addedWeightKg = 5.0)))

        assertEquals(listOf(PlannedSet(values = values(reps = 10))), SetPrefill.forAddedExercise(ExerciseType.BODYWEIGHT, last))
    }

    @Test
    fun `a routine's planned values win over last time`() {
        val planned = SetPrefill.forRoutineExercise(
            SetTarget(sets = 2, reps = 5, weightKg = 100.0),
            ExerciseType.STRENGTH,
            listOf(lift(90.0, 8), lift(90.0, 8)),
        )

        assertEquals(List(2) { PlannedSet(values = values(100.0, 5)) }, planned)
    }

    @Test
    fun `last time fills what the routine leaves blank, set by set, warm-ups skipped`() {
        val planned = SetPrefill.forRoutineExercise(
            SetTarget(sets = 4, reps = 8),
            ExerciseType.STRENGTH,
            listOf(lift(40.0, 10, SetType.WARMUP), lift(80.0, 8), lift(82.5, 6)),
        )

        // The weight comes from last time's working sets in order; the fourth takes the last one.
        assertEquals(listOf(80.0, 82.5, 82.5, 82.5), planned.map { it.values.weightKg })
        assertEquals(List(4) { 8 }, planned.map { it.values.reps })
        assertEquals(List(4) { SetType.NORMAL }, planned.map { it.setType })
    }

    @Test
    fun `a routine exercise never done keeps just its targets`() {
        val planned = SetPrefill.forRoutineExercise(SetTarget(sets = 3, reps = 10), ExerciseType.STRENGTH, emptyList())

        assertEquals(List(3) { PlannedSet(values = values(reps = 10)) }, planned)
    }

    @Test
    fun `an added set starts from the last working set`() {
        val existing = listOf(
            SetCandidate(SetType.WARMUP, isCompleted = true, values(40.0, 10)),
            SetCandidate(SetType.NORMAL, isCompleted = true, values(80.0, 8)),
            SetCandidate(SetType.WARMUP, isCompleted = false, values(50.0, 5)),
        )

        assertEquals(values(80.0, 8), SetPrefill.forAddedSet(ExerciseType.STRENGTH, existing))
        assertEquals(values(40.0, 10), SetPrefill.forAddedSet(ExerciseType.STRENGTH, existing.take(1)))
        assertEquals(SetValues(), SetPrefill.forAddedSet(ExerciseType.STRENGTH, emptyList()))
    }

    @Test
    fun `a completed set's values carry into the empty sets after it, of the same type`() {
        val sets = listOf(
            SetCandidate(SetType.NORMAL, isCompleted = false, values()), // before: untouched
            SetCandidate(SetType.NORMAL, isCompleted = false, values(80.0, 8)), // the one completed
            SetCandidate(SetType.NORMAL, isCompleted = false, values()), // empty: carried into
            SetCandidate(SetType.NORMAL, isCompleted = false, values(reps = 6)), // typed into: kept
            SetCandidate(SetType.DROP, isCompleted = false, values()), // another type: kept
            SetCandidate(SetType.NORMAL, isCompleted = true, values()), // done already: kept
            SetCandidate(SetType.NORMAL, isCompleted = false, values()), // empty: carried into
        )

        assertEquals(listOf(2, 6), SetPrefill.carryForward(ExerciseType.STRENGTH, sets, completedIndex = 1))
    }
}
