package com.example.liftbook.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDecimal
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.core.unit.kgToLb
import com.example.liftbook.core.unit.metersToKilometers
import com.example.liftbook.core.unit.metersToMiles
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.WeightUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * How progress (FR-5) reads. Values arrive in stored units — kilograms, seconds, metres — and are
 * converted here, for display only (FR-6.1).
 */

@StringRes
fun ProgressMetric.labelRes(): Int = when (this) {
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> R.string.metric_one_rep_max
    ProgressMetric.MAX_WEIGHT -> R.string.metric_max_weight
    ProgressMetric.VOLUME -> R.string.metric_volume
    ProgressMetric.MOST_REPS -> R.string.metric_most_reps
    ProgressMetric.TOTAL_REPS -> R.string.metric_total_reps
    ProgressMetric.TOTAL_DURATION -> R.string.metric_duration
    ProgressMetric.TOTAL_DISTANCE -> R.string.metric_distance
}

/** What TalkBack says for a metric: "Estimated one-rep max" rather than "Est. 1RM". */
@StringRes
fun ProgressMetric.spokenLabelRes(): Int = when (this) {
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> R.string.metric_one_rep_max_spoken
    else -> labelRes()
}

/** "1M", "3M", "All". */
@StringRes
fun ProgressRange.labelRes(): Int = when (this) {
    ProgressRange.ONE_MONTH -> R.string.range_one_month
    ProgressRange.THREE_MONTHS -> R.string.range_three_months
    ProgressRange.SIX_MONTHS -> R.string.range_six_months
    ProgressRange.ONE_YEAR -> R.string.range_one_year
    ProgressRange.ALL -> R.string.range_all
}

/** What TalkBack says for a range: "3 months", "All time". */
@StringRes
fun ProgressRange.spokenRes(): Int = when (this) {
    ProgressRange.ONE_MONTH -> R.string.range_one_month_spoken
    ProgressRange.THREE_MONTHS -> R.string.range_three_months_spoken
    ProgressRange.SIX_MONTHS -> R.string.range_six_months_spoken
    ProgressRange.ONE_YEAR -> R.string.range_one_year_spoken
    ProgressRange.ALL -> R.string.range_all_spoken
}

/** The order ranges are offered in, shortest first. */
val ProgressRangeOrder = ProgressRange.entries.toList()

/**
 * A stored value in the unit a chart plots it in: weight in the user's unit, time in minutes —
 * so the gridlines fall on round minutes — distance in km or miles, reps as they are.
 */
fun ProgressMetric.chartValue(value: Double, unit: WeightUnit): Double = when (this) {
    ProgressMetric.ESTIMATED_ONE_REP_MAX, ProgressMetric.MAX_WEIGHT, ProgressMetric.VOLUME ->
        if (unit == WeightUnit.KG) value else kgToLb(value)
    ProgressMetric.MOST_REPS, ProgressMetric.TOTAL_REPS -> value
    ProgressMetric.TOTAL_DURATION -> value / SECONDS_PER_MINUTE
    ProgressMetric.TOTAL_DISTANCE -> if (unit == WeightUnit.KG) metersToKilometers(value) else metersToMiles(value)
}

/** A gridline's label, from a [chartValue]: "110", "2,500", "20:00", "5.5". */
fun ProgressMetric.axisLabel(chartValue: Double, locale: Locale = Locale.getDefault()): String = when (this) {
    ProgressMetric.TOTAL_DURATION -> formatDuration((chartValue * SECONDS_PER_MINUTE).roundToInt(), locale)
    else -> formatDecimal(chartValue, maxFractionDigits = 1, locale = locale)
}

/** A metric's value split into its number and its unit, so the number can take the stronger style. */
data class MetricValueText(val number: String, val unit: String?, val spoken: String)

/**
 * A stored value as the user reads it: "116.7" kg for an estimate (one decimal is all the
 * precision it has), "4,320" kg of volume, "12" reps, "25:00", "5.2" km.
 */
@Composable
fun metricValueText(metric: ProgressMetric, value: Double, unit: WeightUnit): MetricValueText = when (metric) {
    ProgressMetric.ESTIMATED_ONE_REP_MAX, ProgressMetric.MAX_WEIGHT, ProgressMetric.VOLUME -> {
        val digits = when (metric) {
            ProgressMetric.ESTIMATED_ONE_REP_MAX -> 1
            ProgressMetric.VOLUME -> 0
            else -> 2
        }
        val number = displayWeight(value, unit, maxFractionDigits = digits)
        val label = stringResource(unit.weightLabelRes())
        MetricValueText(number, label, "$number $label")
    }
    ProgressMetric.MOST_REPS, ProgressMetric.TOTAL_REPS -> {
        val reps = value.roundToInt()
        MetricValueText(reps.toString(), pluralStringResource(R.plurals.reps_unit, reps), pluralStringResource(R.plurals.reps_count, reps, reps))
    }
    ProgressMetric.TOTAL_DURATION -> {
        val seconds = value.roundToInt()
        MetricValueText(formatDuration(seconds), null, spokenDuration(seconds))
    }
    ProgressMetric.TOTAL_DISTANCE -> {
        val number = displayDistance(value, unit)
        val label = stringResource(unit.distanceLabelRes())
        MetricValueText(number, label, "$number $label")
    }
}

/**
 * A change in a metric, signed: "+8.3 kg", "−2 reps", "+5:00", or "No change". A real minus
 * sign, not a hyphen. TalkBack hears "up 8.3 kg" or "down 2 reps".
 */
@Composable
fun metricChangeText(metric: ProgressMetric, change: Double, unit: WeightUnit): MetricValueText {
    val magnitude = metricValueText(metric, abs(change), unit)
    if (magnitude.number.readsAsZero()) {
        val none = stringResource(R.string.progress_no_change)
        return MetricValueText(none, null, none)
    }
    val text = listOfNotNull(magnitude.number, magnitude.unit).joinToString(NBSP)
    return if (change > 0) {
        MetricValueText(stringResource(R.string.progress_change_up, text), null, stringResource(R.string.progress_change_up_spoken, magnitude.spoken))
    } else {
        MetricValueText(stringResource(R.string.progress_change_down, text), null, stringResource(R.string.progress_change_down_spoken, magnitude.spoken))
    }
}

/** A body weight in the unit its chart plots it in. */
fun bodyWeightChartValue(kg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) kg else kgToLb(kg)

/** A body weight, to the tenth: "82.4 kg". */
@Composable
fun bodyWeightText(kg: Double, unit: WeightUnit): String = weightWithUnit(kg, unit, maxFractionDigits = 1)

/** A signed body-weight change: "−0.6 kg", "+1.2 lb", or "No change". */
@Composable
fun bodyWeightChangeText(changeKg: Double, unit: WeightUnit): MetricValueText {
    val number = displayWeight(abs(changeKg), unit, maxFractionDigits = 1)
    val label = stringResource(unit.weightLabelRes())
    if (number.readsAsZero()) {
        val none = stringResource(R.string.progress_no_change)
        return MetricValueText(none, null, none)
    }
    val text = number + NBSP + label
    return if (changeKg > 0) {
        MetricValueText(stringResource(R.string.progress_change_up, text), null, stringResource(R.string.progress_change_up_spoken, "$number $label"))
    } else {
        MetricValueText(stringResource(R.string.progress_change_down, text), null, stringResource(R.string.progress_change_down_spoken, "$number $label"))
    }
}

/**
 * Whether a formatted number shows as nothing — "0", "0.0", "0:00" — so a change too small to
 * see reads "No change" rather than "+0 kg". Any locale's digits count.
 */
private fun String.readsAsZero(): Boolean = none { it.isDigit() && it.digitToInt() != 0 }

private const val SECONDS_PER_MINUTE = 60.0
