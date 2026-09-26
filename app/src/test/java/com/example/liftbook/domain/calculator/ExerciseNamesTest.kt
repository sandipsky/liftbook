package com.example.liftbook.domain.calculator

import com.example.liftbook.testing.exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseNamesTest {

    private val library = listOf(
        exercise("Bench Press (Barbell)", id = "bench"),
        exercise("Cable Y-Raise", id = "y-raise", isCustom = true),
        exercise("Spider Curl", id = "spider", isCustom = true, isArchived = true),
    )

    @Test
    fun `normalize trims and collapses whitespace`() {
        assertEquals("Cable Y-Raise", ExerciseNames.normalize("  Cable \t  Y-Raise  "))
    }

    @Test
    fun `a new unique name is valid`() {
        assertNull(ExerciseNames.validate("Zercher Squat", library))
    }

    @Test
    fun `blank and whitespace-only names are rejected`() {
        assertEquals(ExerciseNameError.BLANK, ExerciseNames.validate("", library))
        assertEquals(ExerciseNameError.BLANK, ExerciseNames.validate("   ", library))
    }

    @Test
    fun `names longer than the limit are rejected`() {
        val tooLong = "a".repeat(ExerciseNames.MAX_LENGTH + 1)
        assertEquals(ExerciseNameError.TOO_LONG, ExerciseNames.validate(tooLong, library))
        assertNull(ExerciseNames.validate("a".repeat(ExerciseNames.MAX_LENGTH), library))
    }

    @Test
    fun `a name already in the library is a duplicate, ignoring case and spacing`() {
        assertEquals(ExerciseNameError.DUPLICATE, ExerciseNames.validate("bench press (barbell)", library))
        assertEquals(ExerciseNameError.DUPLICATE, ExerciseNames.validate("  Cable   y-raise ", library))
    }

    @Test
    fun `an exercise may keep its own name while being edited`() {
        assertNull(ExerciseNames.validate("Cable Y-Raise", library, editingId = "y-raise"))
    }

    @Test
    fun `an archived exercise doesn't reserve its name`() {
        assertNull(ExerciseNames.validate("Spider Curl", library))
    }
}
