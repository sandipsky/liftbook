package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.WeightUnit
import java.util.Locale

/**
 * What's typed into one set of the workout in progress, as text in the user's units. The text
 * states are owned here — by the ViewModel — so typing never round-trips through the UI state
 * (architecture §5.1); the ViewModel saves [values] as they change.
 *
 * Each field remembers the value it last agreed with the database and the text that showed it.
 * While the text is unchanged that value is used as is, never re-parsed from its rounded text:
 * 80 kg shown as "176.37" lb must not be saved back as 79.9999 kg.
 */
@Stable
class SetFields private constructor(
    val weight: Field<Double>,
    val reps: Field<Int>,
    val duration: Field<Int>,
    val distance: Field<Double>,
) {
    /** The set's values as typed, in stored units. An empty or unreadable field is null. */
    fun values(): SetValues = SetValues(
        weightKg = weight.value(),
        reps = reps.value(),
        durationSeconds = duration.value(),
        distanceMeters = distance.value(),
    )

    /** Every field's text, to notice edits and to record what was saved. */
    fun texts(): List<String> = listOf(weight.text, reps.text, duration.text, distance.text)

    /** Records that [values], typed as [texts], are now what the database holds. */
    fun markSaved(values: SetValues, texts: List<String>) {
        weight.markSaved(values.weightKg, texts[0])
        reps.markSaved(values.reps, texts[1])
        duration.markSaved(values.durationSeconds, texts[2])
        distance.markSaved(values.distanceMeters, texts[3])
    }

    /** Replaces the text with [values], as when a completed set's values carry forward. */
    fun fill(values: SetValues) {
        weight.fill(values.weightKg)
        reps.fill(values.reps)
        duration.fill(values.durationSeconds)
        distance.fill(values.distanceMeters)
    }

    /** One field: its text state, and the value and text it last agreed with the database on. */
    @Stable
    class Field<T : Any> internal constructor(
        initial: T?,
        private val format: (T) -> String,
        private val parse: (String) -> T?,
    ) {
        val state = TextFieldState(initial?.let(format).orEmpty())
        private var savedValue: T? = initial
        private var savedText: String = state.text.toString()

        val text: String get() = state.text.toString()

        fun value(): T? = text.let { if (it == savedText) savedValue else parse(it) }

        internal fun markSaved(value: T?, text: String) {
            savedValue = value
            savedText = text
        }

        internal fun fill(value: T?) {
            val text = value?.let(format).orEmpty()
            state.setTextAndPlaceCursorAtEnd(text)
            markSaved(value, text)
        }
    }

    companion object {
        /** Fields showing [values] in [unit]. */
        fun from(values: SetValues, unit: WeightUnit, locale: Locale = Locale.getDefault()) = SetFields(
            weight = Field(
                initial = values.weightKg,
                format = { formatDecimalForInput(if (unit == WeightUnit.KG) it else kgToLb(it), locale = locale) },
                parse = { text -> parseDecimal(text, locale)?.let { if (unit == WeightUnit.KG) it else lbToKg(it) } },
            ),
            reps = Field(
                initial = values.reps,
                format = { it.toString() },
                parse = { it.toIntOrNull() },
            ),
            duration = Field(
                initial = values.durationSeconds,
                format = ::durationToDigits,
                parse = ::digitsToDuration,
            ),
            distance = Field(
                initial = values.distanceMeters,
                format = {
                    formatDecimalForInput(if (unit == WeightUnit.KG) metersToKilometers(it) else metersToMiles(it), locale = locale)
                },
                parse = { text ->
                    parseDecimal(text, locale)?.let { if (unit == WeightUnit.KG) kilometersToMeters(it) else milesToMeters(it) }
                },
            ),
        )
    }
}

/**
 * A free-text note being typed (FR-3.9), and the note the database last held. Surrounding
 * space is trimmed on save, and a blank note is no note.
 */
@Stable
class NoteText(initial: String?) {
    val state = TextFieldState(initial.orEmpty())
    private var saved: String? = initial

    /** The note as it would be saved. */
    fun current(): String? = state.text.toString().trim().ifEmpty { null }

    fun hasChanged(): Boolean = current() != saved

    fun markSaved(note: String?) {
        saved = note
    }
}
