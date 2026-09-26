package com.example.liftbook.ui.components

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A chart's value axis: its bounds, and the round numbers its gridlines sit at. The bounds are
 * the outer gridlines, so the line never runs into the frame and every gridline has a label a
 * person would write down — 100, 110, 120, never 103.7.
 */
internal data class ChartScale(val min: Double, val max: Double, val ticks: List<Double>) {

    /** Where [value] sits between the bounds: 0 at [min], 1 at [max]. */
    fun fraction(value: Double): Float = if (max > min) ((value - min) / (max - min)).toFloat() else HALF
}

/**
 * The axis for values from [low] to [high], with about [targetTicks] gridlines at a round step.
 * A flat series gets room above and below, so its line sits mid-chart rather than on an edge.
 * Values that can't go negative — reps, weights, time — never get an axis below zero.
 */
internal fun chartScale(low: Double, high: Double, targetTicks: Int = DEFAULT_TICKS): ChartScale {
    require(targetTicks >= 2) { "An axis needs two gridlines" }
    var from = minOf(low, high)
    var to = maxOf(low, high)
    if (to - from < FLAT_EPSILON) {
        val pad = maxOf(abs(to) * FLAT_PAD_FRACTION, 1.0)
        from -= pad
        to += pad
    }
    if (minOf(low, high) >= 0.0) from = from.coerceAtLeast(0.0)
    val step = niceStep((to - from) / (targetTicks - 1))
    val min = floor(from / step + TICK_EPSILON) * step
    val max = ceil(to / step - TICK_EPSILON) * step
    val count = ((max - min) / step).roundToInt() + 1
    return ChartScale(min, max, List(count) { index -> min + index * step })
}

/** The smallest of 1, 2, 2.5 and 5 × 10ⁿ that's at least [raw]: the step between round gridlines. */
internal fun niceStep(raw: Double): Double {
    require(raw > 0.0) { "A step is more than nothing" }
    val magnitude = 10.0.pow(floor(log10(raw)))
    // Between 1 and 10, since the magnitude is the power of ten at or below the step.
    val fraction = raw / magnitude
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

private const val DEFAULT_TICKS = 4
private const val HALF = 0.5f
private const val FLAT_EPSILON = 1e-9

/** A flat line gets 2% either side: body weight moves by less than that, so more would flatten it. */
private const val FLAT_PAD_FRACTION = 0.02

/** Absorbs floating-point error, so 120 / 10 counts as exactly 12 steps. */
private const val TICK_EPSILON = 1e-9
