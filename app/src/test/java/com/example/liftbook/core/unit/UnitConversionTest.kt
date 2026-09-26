package com.example.liftbook.core.unit

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConversionTest {

    @Test
    fun `one pound is exactly 0_45359237 kg`() {
        assertEquals(0.45359237, lbToKg(1.0), 0.0)
        assertEquals(1.0, kgToLb(0.45359237), 0.0)
    }

    @Test
    fun `kilograms convert to pounds`() {
        assertEquals(220.46226218487757, kgToLb(100.0), 1e-9)
        assertEquals(0.0, kgToLb(0.0), 0.0)
    }

    @Test
    fun `a pound value survives a round trip through kilograms`() {
        // Stored as kg, shown as lb: 185 lb must come back as 185, not 184.99.
        listOf(45.0, 135.0, 185.0, 225.0, 315.0, 2.5).forEach { lb ->
            assertEquals(lb, kgToLb(lbToKg(lb)), 1e-9)
        }
    }

    @Test
    fun `metres convert to kilometres and miles`() {
        assertEquals(5.2, metersToKilometers(5_200.0), 1e-12)
        assertEquals(1.0, metersToMiles(1_609.344), 1e-12)
        assertEquals(3.1068559611866697, metersToMiles(5_000.0), 1e-12)
    }
}
