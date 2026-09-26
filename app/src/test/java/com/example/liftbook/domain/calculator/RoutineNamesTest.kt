package com.example.liftbook.domain.calculator

import com.example.liftbook.testing.routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineNamesTest {

    private val routines = listOf(routine("Push", id = "push"), routine("Pull", id = "pull"))

    @Test
    fun `a new, unique name is valid`() {
        assertNull(RoutineNames.validate("Legs", routines))
    }

    @Test
    fun `a blank name is rejected`() {
        assertEquals(RoutineNameError.BLANK, RoutineNames.validate("   ", routines))
    }

    @Test
    fun `an overlong name is rejected`() {
        assertEquals(RoutineNameError.TOO_LONG, RoutineNames.validate("x".repeat(RoutineNames.MAX_LENGTH + 1), routines))
        assertNull(RoutineNames.validate("x".repeat(RoutineNames.MAX_LENGTH), routines))
    }

    @Test
    fun `names clash ignoring case and spacing`() {
        assertEquals(RoutineNameError.DUPLICATE, RoutineNames.validate("  push ", routines))
        assertEquals(RoutineNameError.DUPLICATE, RoutineNames.validate("PULL", routines))
    }

    @Test
    fun `a routine being edited may keep its name`() {
        assertNull(RoutineNames.validate("Push", routines, editingId = "push"))
        assertEquals(RoutineNameError.DUPLICATE, RoutineNames.validate("Pull", routines, editingId = "push"))
    }

    @Test
    fun `a copy takes the next free number`() {
        assertEquals("Push 2", RoutineNames.copyName("Push", listOf("Push")))
        assertEquals("Push 3", RoutineNames.copyName("Push", listOf("Push", "Push 2")))
        assertEquals("Push 3", RoutineNames.copyName("Push", listOf("Push", "push 2")))
    }

    @Test
    fun `a name ending in a number counts on from it`() {
        assertEquals("Day 2", RoutineNames.copyName("Day 1", listOf("Day 1")))
        assertEquals("Day 3", RoutineNames.copyName("Day 1", listOf("Day 1", "Day 2")))
        assertEquals("Push 3", RoutineNames.copyName("Push 2", listOf("Push", "Push 2")))
    }

    @Test
    fun `a number that's part of a word isn't counted on`() {
        assertEquals("5x5 2", RoutineNames.copyName("5x5", listOf("5x5")))
    }

    @Test
    fun `a copy of a long name is shortened to fit`() {
        val long = "Upper body strength and hypertrophy block"
        val copy = RoutineNames.copyName(long, listOf(long))
        assertEquals(RoutineNames.MAX_LENGTH, copy.length)
        assertTrue(copy.endsWith(" 2"))
        assertNull(RoutineNames.validate(copy, listOf(routine(long))))
    }
}
