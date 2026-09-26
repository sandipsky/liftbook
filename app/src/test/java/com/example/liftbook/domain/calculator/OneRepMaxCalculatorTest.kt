package com.example.liftbook.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class OneRepMaxCalculatorTest {

    @Test
    fun `Epley estimate is weight times one plus reps over thirty`() {
        assertEquals(133.333_333, oneRepMax(100.0, 10), 1e-6)
    }

    @Test
    fun `reps under thirty still count — no integer division`() {
        // With integer division, 5 / 30 is 0 and the estimate would be just the weight.
        assertEquals(116.666_667, oneRepMax(100.0, 5), 1e-6)
    }

    @Test
    fun `a single rep estimates a little over the weight lifted`() {
        assertEquals(103.333_333, oneRepMax(100.0, 1), 1e-6)
    }
}
