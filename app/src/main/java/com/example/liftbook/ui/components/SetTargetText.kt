package com.example.liftbook.ui.components

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers

/**
 * A routine target with the numbers emphasised and the units receding, like [SetMetricsText]:
 * "3 × 10 · 80 kg", "3 × 10", "4 sets", "3 × 0:45", "20:00 · 5 km". Only the values the exercise
 * type records are shown, in the user's unit (FR-6.1). TalkBack hears "3 sets of 10 reps, 80 kg".
 */
@Composable
fun SetTargetText(
    target: SetTarget,
    type: ExerciseType,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val number = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    val unit = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val times = stringResource(R.string.set_times)
    val separator = stringResource(R.string.set_separator)
    val shown = target.applicableTo(type)
    val sets = shown.sets
    val setsSpoken = pluralStringResource(R.plurals.sets_count, sets, sets)
    val setsUnit = pluralStringResource(R.plurals.sets_unit, sets)

    // What each set aims for: a number of reps or a duration.
    val each: String?
    val eachSpoken: String?
    when {
        shown.reps != null -> {
            each = shown.reps.toString()
            eachSpoken = pluralStringResource(R.plurals.reps_count, shown.reps, shown.reps)
        }
        shown.durationSeconds != null -> {
            each = formatDuration(shown.durationSeconds)
            eachSpoken = spokenDuration(shown.durationSeconds)
        }
        else -> {
            each = null
            eachSpoken = null
        }
    }
    // A single timed set reads as just its time: "20:00", not "1 × 20:00".
    val eachAlone = sets == 1 && shown.durationSeconds != null

    // And what it's done with: a weight or a distance.
    val load = shown.weightKg?.takeIf { it > 0.0 }?.let { displayWeight(it, weightUnit) }
    val distance = shown.distanceMeters?.takeIf { it > 0.0 }?.let { displayDistance(it, weightUnit) }
    val extra = load ?: distance
    val extraLabel = stringResource(if (load != null) weightUnit.weightLabelRes() else weightUnit.distanceLabelRes())

    val text = buildAnnotatedString {
        when {
            each != null && eachAlone -> withStyle(number) { append(each) }
            each != null -> {
                withStyle(number) { append(sets.toString()) }
                withStyle(unit) { append(NBSP + times + NBSP) }
                withStyle(number) { append(each) }
            }
            else -> {
                withStyle(number) { append(sets.toString()) }
                withStyle(unit) { append(NBSP + setsUnit) }
            }
        }
        if (extra != null) {
            withStyle(unit) { append(" $separator ") }
            withStyle(number) { append(extra) }
            withStyle(unit) { append(NBSP + extraLabel) }
        }
    }
    val base = when {
        eachSpoken != null && eachAlone -> eachSpoken
        eachSpoken != null -> stringResource(R.string.target_spoken_of, setsSpoken, eachSpoken)
        else -> setsSpoken
    }
    val spoken = if (extra != null) stringResource(R.string.target_spoken_with, base, "$extra $extraLabel") else base

    Text(
        text = text,
        style = style.tabularNumbers(),
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

@ThemePreviews
@Composable
private fun SetTargetTextPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter)) {
            SetTargetText(SetTarget(sets = 3, reps = 8, weightKg = 80.0), ExerciseType.STRENGTH, WeightUnit.KG)
            SetTargetText(SetTarget(sets = 3, reps = 8, weightKg = 80.0), ExerciseType.STRENGTH, WeightUnit.LB)
            SetTargetText(SetTarget(sets = 4), ExerciseType.STRENGTH, WeightUnit.KG)
            SetTargetText(SetTarget(sets = 3, reps = 12), ExerciseType.BODYWEIGHT, WeightUnit.KG)
            SetTargetText(SetTarget(sets = 3, durationSeconds = 45), ExerciseType.CARDIO, WeightUnit.KG)
            SetTargetText(SetTarget(sets = 1, durationSeconds = 1_200, distanceMeters = 5_000.0), ExerciseType.CARDIO, WeightUnit.KG)
        }
    }
}
