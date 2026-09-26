package com.example.liftbook.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class InputFormattingTest {

    private val us = Locale.US
    private val germany = Locale.GERMANY

    @Test
    fun `values to edit are ungrouped and drop trailing zeros`() {
        assertEquals("80", formatDecimalForInput(80.0, locale = us))
        assertEquals("82.5", formatDecimalForInput(82.5, locale = us))
        assertEquals("1234.57", formatDecimalForInput(1_234.567, locale = us))
        assertEquals("0", formatDecimalForInput(0.0, locale = us))
        assertEquals("100", formatDecimalForInput(100.0, locale = us))
    }

    @Test
    fun `values to edit use the locale's decimal mark`() {
        assertEquals("82,5", formatDecimalForInput(82.5, locale = germany))
    }

    @Test
    fun `either decimal mark is read, whatever the locale`() {
        assertEquals(82.5, parseDecimal("82.5", us)!!, 0.0)
        assertEquals(82.5, parseDecimal("82,5", us)!!, 0.0)
        assertEquals(82.5, parseDecimal("82,5", germany)!!, 0.0)
        assertEquals(82.5, parseDecimal("82.5", germany)!!, 0.0)
        assertEquals(80.0, parseDecimal(" 80 ", us)!!, 0.0)
        assertEquals(0.5, parseDecimal(".5", us)!!, 0.0)
    }

    @Test
    fun `anything but a plain number reads as nothing`() {
        assertNull(parseDecimal("", us))
        assertNull(parseDecimal(".", us))
        assertNull(parseDecimal("1.2.3", us))
        assertNull(parseDecimal("80kg", us))
        assertNull(parseDecimal("-5", us))
    }

    @Test
    fun `formatted input parses back to the value`() {
        listOf(us, germany).forEach { locale ->
            listOf(0.5, 20.0, 82.5, 176.37, 1_000.25).forEach { value ->
                assertEquals(value, parseDecimal(formatDecimalForInput(value, locale = locale), locale)!!, 0.0)
            }
        }
    }

    @Test
    fun `durations are typed as digits filling from the right`() {
        assertEquals(5, digitsToDuration("5"))
        assertEquals(45, digitsToDuration("45"))
        assertEquals(90, digitsToDuration("130"))
        assertEquals(1_200, digitsToDuration("2000"))
        assertNull(digitsToDuration(""))
        assertNull(digitsToDuration("1:30"))
    }

    @Test
    fun `too many seconds still count as seconds`() {
        // "90" is 0:90 — ninety seconds, which the field shows back as 1:30.
        assertEquals(90, digitsToDuration("90"))
        assertEquals("130", durationToDigits(digitsToDuration("90")!!))
    }

    @Test
    fun `durations become digits and back`() {
        assertEquals("45", durationToDigits(45))
        assertEquals("130", durationToDigits(90))
        assertEquals("1500", durationToDigits(900))
        assertEquals("0", durationToDigits(0))
        listOf(1, 59, 60, 61, 599, 3_600, MAX_DURATION_INPUT_SECONDS).forEach {
            assertEquals(it, digitsToDuration(durationToDigits(it)))
        }
    }

    @Test
    fun `durations beyond the field are capped`() {
        assertEquals("9959", durationToDigits(MAX_DURATION_INPUT_SECONDS + 1_000))
    }
}
