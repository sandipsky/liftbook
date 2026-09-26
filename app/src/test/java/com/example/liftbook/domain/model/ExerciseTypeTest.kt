package com.example.liftbook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseTypeTest {

    @Test
    fun `nothing chosen yet suggests weight and reps`() {
        assertEquals(ExerciseType.STRENGTH, ExerciseType.suggestedFor(null, null))
    }

    @Test
    fun `loaded equipment suggests weight and reps`() {
        assertEquals(ExerciseType.STRENGTH, ExerciseType.suggestedFor(MuscleGroup.CHEST, Equipment.BARBELL))
        assertEquals(ExerciseType.STRENGTH, ExerciseType.suggestedFor(null, Equipment.CABLE))
    }

    @Test
    fun `no equipment or a band suggests reps only`() {
        assertEquals(ExerciseType.BODYWEIGHT, ExerciseType.suggestedFor(MuscleGroup.BACK, Equipment.NONE))
        assertEquals(ExerciseType.BODYWEIGHT, ExerciseType.suggestedFor(MuscleGroup.SHOULDERS, Equipment.BAND))
    }

    @Test
    fun `cardio muscle group or a cardio machine suggests time and distance`() {
        assertEquals(ExerciseType.CARDIO, ExerciseType.suggestedFor(MuscleGroup.CARDIO, Equipment.NONE))
        assertEquals(ExerciseType.CARDIO, ExerciseType.suggestedFor(MuscleGroup.QUADS, Equipment.CARDIO_MACHINE))
        assertEquals(ExerciseType.CARDIO, ExerciseType.suggestedFor(MuscleGroup.CARDIO, null))
    }
}
