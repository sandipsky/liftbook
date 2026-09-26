package com.example.liftbook.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDecimal
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.core.unit.kgToLb
import com.example.liftbook.core.unit.metersToKilometers
import com.example.liftbook.core.unit.metersToMiles
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.util.Locale

/**
 * A set's values with the numbers emphasised and the units receding — "80 kg × 5", "12 reps",
 * "+10 kg × 8", "25:00 · 5.2 km". Converts from stored kilograms and metres to the user's unit
 * for display only (FR-6.1). Tabular figures keep a column of sets aligned. TalkBack hears a
 * spoken form ("80 kg, 5 reps") instead of the symbols.
 */
@Composable
fun SetMetricsText(
    metrics: SetMetrics,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val number = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    val unit = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val weightLabel = stringResource(weightUnit.weightLabelRes())
    val times = stringResource(R.string.set_times)
    val separator = stringResource(R.string.set_separator)

    // Resolve every string first; the builders below only assemble and style them.
    val text: AnnotatedString
    val spoken: String
    when (metrics) {
        is SetMetrics.Strength -> {
            val weight = displayWeight(metrics.weightKg, weightUnit)
            val reps = pluralStringResource(R.plurals.reps_count, metrics.reps, metrics.reps)
            text = buildAnnotatedString {
                withStyle(number) { append(weight) }
                withStyle(unit) { append(NBSP + weightLabel + NBSP + times + NBSP) }
                withStyle(number) { append(metrics.reps.toString()) }
            }
            spoken = stringResource(R.string.set_spoken_weighted, "$weight $weightLabel", reps)
        }
        is SetMetrics.Bodyweight -> {
            val reps = pluralStringResource(R.plurals.reps_count, metrics.reps, metrics.reps)
            val repsUnit = pluralStringResource(R.plurals.reps_unit, metrics.reps)
            val added = metrics.addedWeightKg?.takeIf { it > 0.0 }?.let { displayWeight(it, weightUnit) }
            val addedText = added?.let { stringResource(R.string.set_added_weight, it) }
            text = buildAnnotatedString {
                if (addedText == null) {
                    withStyle(number) { append(metrics.reps.toString()) }
                    withStyle(unit) { append(NBSP + repsUnit) }
                } else {
                    withStyle(number) { append(addedText) }
                    withStyle(unit) { append(NBSP + weightLabel + NBSP + times + NBSP) }
                    withStyle(number) { append(metrics.reps.toString()) }
                }
            }
            spoken = if (added == null) {
                reps
            } else {
                stringResource(R.string.set_spoken_added_weight, reps, "$added $weightLabel")
            }
        }
        is SetMetrics.Cardio -> {
            val duration = formatDuration(metrics.durationSeconds)
            val distance = metrics.distanceMeters?.takeIf { it > 0.0 }?.let { displayDistance(it, weightUnit) }
            val distanceLabel = stringResource(weightUnit.distanceLabelRes())
            val spokenDuration = spokenDuration(metrics.durationSeconds)
            text = buildAnnotatedString {
                withStyle(number) { append(duration) }
                if (distance != null) {
                    withStyle(unit) { append(NBSP + separator + NBSP) }
                    withStyle(number) { append(distance) }
                    withStyle(unit) { append(NBSP + distanceLabel) }
                }
            }
            spoken = if (distance == null) {
                spokenDuration
            } else {
                stringResource(R.string.set_spoken_distance, spokenDuration, "$distance $distanceLabel")
            }
        }
    }

    Text(
        text = text,
        style = style.tabularNumbers(),
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

/**
 * Weight in the user's unit, number only: 82.5 kg → "82.5", or "181.88" in pounds. Totals such as
 * volume read better whole, with [maxFractionDigits] = 0: "4,320".
 */
fun displayWeight(
    kg: Double,
    unit: WeightUnit,
    locale: Locale = Locale.getDefault(),
    maxFractionDigits: Int = 2,
): String = formatDecimal(if (unit == WeightUnit.KG) kg else kgToLb(kg), maxFractionDigits = maxFractionDigits, locale = locale)

/** Distance in the user's unit system, number only: kilometres with kg, miles with lb. */
fun displayDistance(meters: Double, unit: WeightUnit, locale: Locale = Locale.getDefault()): String =
    formatDecimal(if (unit == WeightUnit.KG) metersToKilometers(meters) else metersToMiles(meters), locale = locale)

@StringRes
fun WeightUnit.weightLabelRes(): Int = when (this) {
    WeightUnit.KG -> R.string.unit_kg
    WeightUnit.LB -> R.string.unit_lb
}

@StringRes
fun WeightUnit.distanceLabelRes(): Int = when (this) {
    WeightUnit.KG -> R.string.unit_km
    WeightUnit.LB -> R.string.unit_mi
}

/** "1 hour 5 minutes", "45 seconds" — a clock reading like "1:05:00" is read out badly. */
@Composable
internal fun spokenDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    val parts = buildList {
        if (hours > 0) add(pluralStringResource(R.plurals.duration_hours, hours, hours))
        if (minutes > 0) add(pluralStringResource(R.plurals.duration_minutes, minutes, minutes))
        if (seconds > 0 || (hours == 0 && minutes == 0)) {
            add(pluralStringResource(R.plurals.duration_seconds, seconds, seconds))
        }
    }
    return parts.joinToString(" ")
}

/** Keeps a number and its unit on one line. */
internal const val NBSP = " "

@ThemePreviews
@Composable
private fun SetMetricsTextPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter)) {
            SetMetricsText(SetMetrics.Strength(weightKg = 82.5, reps = 5), WeightUnit.KG)
            SetMetricsText(SetMetrics.Strength(weightKg = 82.5, reps = 5), WeightUnit.LB)
            SetMetricsText(SetMetrics.Bodyweight(reps = 12, addedWeightKg = null), WeightUnit.KG)
            SetMetricsText(SetMetrics.Bodyweight(reps = 8, addedWeightKg = 10.0), WeightUnit.KG)
            SetMetricsText(SetMetrics.Cardio(durationSeconds = 1_500, distanceMeters = 5_200.0), WeightUnit.KG)
            SetMetricsText(
                SetMetrics.Strength(weightKg = 100.0, reps = 3),
                WeightUnit.KG,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
