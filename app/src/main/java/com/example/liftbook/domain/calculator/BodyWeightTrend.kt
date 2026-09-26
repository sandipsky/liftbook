package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.TrendPoint

/**
 * The body-weight trend (FR-5.4): at each weigh-in, the average of every weigh-in in the
 * [windowDays] days ending that day. Day-to-day weight swings by a kilo or more with water and
 * food; the average shows where it's heading. Counting calendar days rather than entries keeps
 * the trend honest when weigh-ins are irregular — a week of daily entries and one entry a week
 * later aren't averaged as if they were neighbours.
 *
 * Oldest first, one point per entry.
 */
fun bodyWeightTrend(entries: List<BodyWeightEntry>, windowDays: Int = TREND_WINDOW_DAYS): List<TrendPoint> {
    require(windowDays >= 1) { "The window needs at least a day" }
    val sorted = entries.sortedBy { it.date }
    val trend = ArrayList<TrendPoint>(sorted.size)
    var windowStart = 0
    var sum = 0.0
    sorted.forEachIndexed { index, entry ->
        sum += entry.weightKg
        val earliest = entry.date.minusDays(windowDays - 1L)
        while (sorted[windowStart].date.isBefore(earliest)) {
            sum -= sorted[windowStart].weightKg
            windowStart++
        }
        trend += TrendPoint(entry.date, sum / (index - windowStart + 1))
    }
    return trend
}

/** How far the trend moved from its first point to its last; null with fewer than two. */
fun List<TrendPoint>.change(): Double? = if (size < 2) null else last().weightKg - first().weightKg

/** A week: long enough to smooth out water weight, short enough to follow a real change. */
const val TREND_WINDOW_DAYS = 7
