package com.example.liftbook.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartScaleTest {

    private fun assertTicks(expected: List<Double>, scale: ChartScale) {
        assertEquals(expected.size, scale.ticks.size)
        expected.zip(scale.ticks).forEach { (want, got) -> assertEquals(want, got, 1e-9) }
    }

    @Test
    fun `gridlines fall on round numbers around the data`() {
        val scale = chartScale(100.0, 116.7)

        assertEquals(100.0, scale.min, 1e-9)
        assertEquals(120.0, scale.max, 1e-9)
        assertTicks(listOf(100.0, 110.0, 120.0), scale)
    }

    @Test
    fun `steps are 1, 2, 2·5 or 5 of a power of ten`() {
        assertEquals(1.0, niceStep(0.9), 1e-12)
        assertEquals(2.0, niceStep(1.3), 1e-12)
        assertEquals(2.5, niceStep(2.2), 1e-12)
        assertEquals(5.0, niceStep(4.1), 1e-12)
        assertEquals(10.0, niceStep(5.6), 1e-12)
        assertEquals(2_000.0, niceStep(1_400.0), 1e-9)
        assertEquals(0.5, niceStep(0.45), 1e-12)
    }

    @Test
    fun `a flat series gets room above and below`() {
        val scale = chartScale(80.0, 80.0)

        assertTrue(scale.min < 80.0 && scale.max > 80.0)
        assertEquals(0.5f, scale.fraction(80.0), 1e-6f)
    }

    @Test
    fun `a single weigh-in keeps a close-up axis, not one that flattens a kilo`() {
        val scale = chartScale(82.4, 82.4)

        assertTicks(listOf(80.0, 82.0, 84.0, 86.0), scale)
    }

    @Test
    fun `values that can't be negative never get an axis below zero`() {
        assertEquals(0.0, chartScale(0.0, 0.0).min, 0.0)
        assertEquals(0.0, chartScale(1_200.0, 5_400.0).min, 0.0)
    }

    @Test
    fun `floating-point steps still reach the top gridline`() {
        // 0.3 / 0.1 is 2.9999… in floating point; the top gridline mustn't be lost to it.
        val scale = chartScale(0.0, 0.3)

        assertEquals(scale.max, scale.ticks.last(), 1e-9)
        assertTrue(scale.max >= 0.3 - 1e-9)
    }

    @Test
    fun `a value's place runs from 0 at the bottom to 1 at the top`() {
        val scale = chartScale(100.0, 116.7)

        assertEquals(0f, scale.fraction(100.0), 1e-6f)
        assertEquals(0.5f, scale.fraction(110.0), 1e-6f)
        assertEquals(1f, scale.fraction(120.0), 1e-6f)
    }
}
