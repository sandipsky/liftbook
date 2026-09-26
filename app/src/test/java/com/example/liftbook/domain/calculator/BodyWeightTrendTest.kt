package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.TrendPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BodyWeightTrendTest {

    private val start = LocalDate.of(2026, 9, 1)

    private fun weighIn(day: Long, kg: Double) = BodyWeightEntry(id = "e$day", date = start.plusDays(day), weightKg = kg)

    @Test
    fun `each point averages the weigh-ins in the week up to it`() {
        val trend = bodyWeightTrend(listOf(weighIn(0, 80.0), weighIn(2, 82.0), weighIn(6, 84.0), weighIn(7, 86.0)))

        assertEquals(start, trend[0].date)
        assertEquals(80.0, trend[0].weightKg, 1e-9)
        assertEquals(81.0, trend[1].weightKg, 1e-9)
        // Day 6 still reaches back to day 0; day 7 no longer does.
        assertEquals(82.0, trend[2].weightKg, 1e-9)
        assertEquals((82.0 + 84.0 + 86.0) / 3, trend[3].weightKg, 1e-9)
    }

    @Test
    fun `a gap in weigh-ins isn't averaged across`() {
        val trend = bodyWeightTrend(listOf(weighIn(0, 90.0), weighIn(30, 80.0)))

        assertEquals(listOf(TrendPoint(start, 90.0), TrendPoint(start.plusDays(30), 80.0)), trend)
    }

    @Test
    fun `the order the weigh-ins come in doesn't matter`() {
        val entries = listOf(weighIn(3, 81.0), weighIn(0, 80.0), weighIn(1, 82.0))

        assertEquals(bodyWeightTrend(entries.sortedBy { it.date }), bodyWeightTrend(entries))
    }

    @Test
    fun `the change is from the first point to the last, and needs two`() {
        val trend = listOf(TrendPoint(start, 84.0), TrendPoint(start.plusDays(10), 83.1), TrendPoint(start.plusDays(20), 82.5))

        assertEquals(-1.5, trend.change()!!, 1e-9)
        assertNull(trend.take(1).change())
        assertNull(emptyList<TrendPoint>().change())
    }

    @Test
    fun `no weigh-ins, no trend`() {
        assertEquals(emptyList<TrendPoint>(), bodyWeightTrend(emptyList()))
    }
}
