package com.example.liftbook.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NumberFormattingTest {

    private val us = Locale.US

    @Test
    fun `whole numbers have no decimals`() {
        assertEquals("80", formatDecimal(80.0, locale = us))
        assertEquals("0", formatDecimal(0.0, locale = us))
    }

    @Test
    fun `trailing zeros are dropped`() {
        assertEquals("82.5", formatDecimal(82.5, locale = us))
        assertEquals("81.25", formatDecimal(81.25, locale = us))
    }

    @Test
    fun `values round to two decimals, half up`() {
        assertEquals("1,234.57", formatDecimal(1_234.567, locale = us))
        assertEquals("0.13", formatDecimal(0.125, locale = us))
    }

    @Test
    fun `the maximum number of decimals is configurable`() {
        assertEquals("82.4", formatDecimal(82.44, maxFractionDigits = 1, locale = us))
    }

    @Test
    fun `the locale's separators are used`() {
        assertEquals("82,5", formatDecimal(82.5, locale = Locale.GERMANY))
        assertEquals("1.234,57", formatDecimal(1_234.567, locale = Locale.GERMANY))
    }

    @Test
    fun `durations read like a clock`() {
        assertEquals("0:00", formatDuration(0, us))
        assertEquals("0:45", formatDuration(45, us))
        assertEquals("25:00", formatDuration(1_500, us))
        assertEquals("1:05:30", formatDuration(3_930, us))
        assertEquals("0:00", formatDuration(-5, us))
    }
}
