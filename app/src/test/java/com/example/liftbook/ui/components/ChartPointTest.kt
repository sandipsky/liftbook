package com.example.liftbook.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.floor

class ChartPointTest {

    @Test
    fun `two workouts on one day are placed apart, by the time each started`() {
        val morning = ChartPoint.at(Instant.parse("2026-09-26T07:00:00Z"), ZoneOffset.UTC, 100.0)
        val evening = ChartPoint.at(Instant.parse("2026-09-26T19:00:00Z"), ZoneOffset.UTC, 110.0)

        assertEquals(0.5, evening.x - morning.x, 1e-9)
    }

    @Test
    fun `a moment falls on the day it is where the user is`() {
        // 23:30 UTC on the 20th is 05:15 on the 21st in Kathmandu.
        val point = ChartPoint.at(Instant.parse("2026-09-20T23:30:00Z"), ZoneId.of("Asia/Kathmandu"), 1.0)

        assertEquals(LocalDate.of(2026, 9, 21).toEpochDay().toDouble(), floor(point.x), 0.0)
    }

    @Test
    fun `a whole day sits at its start, before any moment of it`() {
        val day = LocalDate.of(2026, 9, 26)

        assertTrue(ChartPoint.on(day, 1.0).x < ChartPoint.at(Instant.parse("2026-09-26T00:00:01Z"), ZoneOffset.UTC, 1.0).x)
    }
}
