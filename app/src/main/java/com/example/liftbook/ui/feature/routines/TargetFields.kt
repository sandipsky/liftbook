package com.example.liftbook.ui.feature.routines

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import com.example.liftbook.core.format.digitsToDuration
import com.example.liftbook.core.format.durationToDigits
import com.example.liftbook.core.format.formatDecimalForInput
import com.example.liftbook.core.format.parseDecimal
import com.example.liftbook.core.unit.kgToLb
import com.example.liftbook.core.unit.kilometersToMeters
import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.core.unit.metersToKilometers
import com.example.liftbook.core.unit.metersToMiles
import com.example.liftbook.core.unit.milesToMeters
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.WeightUnit
import java.util.Locale

/**
 * The editable target of one routine exercise, as text in the user's units. The text states are
 * owned here — by the ViewModel — so typing never round-trips through the UI state.
 *
 * A weight or distance the user doesn't touch is saved exactly as it was loaded, not re-parsed
 * from its rounded display text: 80 kg shown as "176.37" lb must not come back as 79.9999 kg.
 */
@Stable
class TargetFields private constructor(
    private val initial: SetTarget,
    private val initialWeightText: String,
    private val initialDistanceText: String,
    val sets: TextFieldState,
    val reps: TextFieldState,
    val weight: TextFieldState,
    val duration: TextFieldState,
    val distance: TextFieldState,
) {
    /** The texts as they are now, to tell whether anything changed. */
    fun texts(): TargetTexts = TargetTexts(
        sets = sets.text.toString(),
        reps = reps.text.toString(),
        weight = weight.text.toString(),
        duration = duration.text.toString(),
        distance = distance.text.toString(),
    )

    /** Whether the set count is one the routine can hold, [SetTarget.MIN_SETS]–[SetTarget.MAX_SETS]. */
    fun hasValidSets(): Boolean = parseSets() != null

    /**
     * The target as typed, stored metric (FR-6.1), or null if the set count is invalid. A blank
     * or zero value means no target for it. Every value is kept, even ones the exercise's type
     * doesn't show, so a type change later doesn't lose them.
     */
    fun toTarget(unit: WeightUnit, locale: Locale = Locale.getDefault()): SetTarget? {
        val setCount = parseSets() ?: return null
        val weightText = weight.text.toString()
        val distanceText = distance.text.toString()
        return SetTarget(
            sets = setCount,
            reps = reps.text.toString().toIntOrNull()?.takeIf { it > 0 },
            weightKg = if (weightText == initialWeightText) {
                initial.weightKg
            } else {
                parseDecimal(weightText, locale)?.takeIf { it > 0.0 }?.let { if (unit == WeightUnit.KG) it else lbToKg(it) }
            },
            durationSeconds = digitsToDuration(duration.text.toString())?.takeIf { it > 0 },
            distanceMeters = if (distanceText == initialDistanceText) {
                initial.distanceMeters
            } else {
                parseDecimal(distanceText, locale)?.takeIf { it > 0.0 }
                    ?.let { if (unit == WeightUnit.KG) kilometersToMeters(it) else milesToMeters(it) }
            },
        )
    }

    private fun parseSets(): Int? =
        sets.text.toString().toIntOrNull()?.takeIf { it in SetTarget.MIN_SETS..SetTarget.MAX_SETS }

    companion object {
        /** Fields showing [target] in [unit]. */
        fun from(target: SetTarget, unit: WeightUnit, locale: Locale = Locale.getDefault()): TargetFields {
            val weightText = target.weightKg
                ?.let { formatDecimalForInput(if (unit == WeightUnit.KG) it else kgToLb(it), locale = locale) }
                .orEmpty()
            val distanceText = target.distanceMeters
                ?.let {
                    formatDecimalForInput(if (unit == WeightUnit.KG) metersToKilometers(it) else metersToMiles(it), locale = locale)
                }
                .orEmpty()
            return TargetFields(
                initial = target,
                initialWeightText = weightText,
                initialDistanceText = distanceText,
                sets = TextFieldState(target.sets.toString()),
                reps = TextFieldState(target.reps?.toString().orEmpty()),
                weight = TextFieldState(weightText),
                duration = TextFieldState(target.durationSeconds?.let(::durationToDigits).orEmpty()),
                distance = TextFieldState(distanceText),
            )
        }
    }
}

data class TargetTexts(
    val sets: String,
    val reps: String,
    val weight: String,
    val duration: String,
    val distance: String,
)
