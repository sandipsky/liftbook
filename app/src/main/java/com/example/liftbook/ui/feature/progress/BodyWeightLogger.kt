package com.example.liftbook.ui.feature.progress

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import com.example.liftbook.core.format.formatDecimalForInput
import com.example.liftbook.core.format.parseDecimal
import com.example.liftbook.core.unit.kgToLb
import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.repository.BodyWeightRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A weigh-in being logged or changed (FR-5.4): the sheet that asks for it is open. */
data class BodyWeightLogState(
    val date: LocalDate,
    /** The weigh-in [date] already has, which saving replaces; null for a day without one. */
    val existing: BodyWeightEntry?,
    /** Opened from a weigh-in to change it, so it offers Delete. */
    val isEditing: Boolean = false,
    /** Save was tried without a weight: the field says so. */
    val showError: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface BodyWeightLogEvent {
    /** A weigh-in was deleted; [entry] is what Undo puts back. */
    data class Deleted(val entry: BodyWeightEntry) : BodyWeightLogEvent

    data object SaveFailed : BodyWeightLogEvent
}

/**
 * Logging body weight (FR-5.4), for any screen that offers it: the sheet's state, the weight
 * being typed, and the writes. Owned by a ViewModel, which keeps it told of the weigh-ins and the
 * unit through [update].
 *
 * A new day's field starts with the last weight logged, selected, since it rarely moves much:
 * typing replaces it, and a small change is a tap away. A day that has a weigh-in starts with that
 * one. Either way, a value saved untouched is saved exactly as it was — never re-read from its
 * rounded text — so 80 kg shown as "176.37" lb isn't stored back as 79.9999 kg.
 */
@Stable
class BodyWeightLogger(
    private val repository: BodyWeightRepository,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate,
) {
    /** What's typed, in the user's unit. Owned here, so typing never round-trips through UI state. */
    val weight = TextFieldState()

    private val _state = MutableStateFlow<BodyWeightLogState?>(null)
    val state: StateFlow<BodyWeightLogState?> = _state.asStateFlow()

    private val _events = Channel<BodyWeightLogEvent>(Channel.BUFFERED)
    val events: Flow<BodyWeightLogEvent> = _events.receiveAsFlow()

    private var entries: List<BodyWeightEntry> = emptyList()
    private var unit = WeightUnit.KG

    // What the field was last filled with, and the text that showed it.
    private var filledKg: Double? = null
    private var filledText: String = ""

    fun update(entries: List<BodyWeightEntry>, unit: WeightUnit) {
        this.entries = entries
        this.unit = unit
    }

    /** Opens the sheet for today. */
    fun open() = openFor(today(), editing = false)

    /** Opens the sheet on [entry], to change or delete it. */
    fun edit(entry: BodyWeightEntry) = openFor(entry.date, editing = true)

    fun changeDate(date: LocalDate) {
        val current = _state.value ?: return
        if (date.isAfter(today())) return
        val existing = entries.firstOrNull { it.date == date }
        // A day with a weigh-in shows it, ready to change; otherwise what's typed stays.
        if (existing != null) fill(existing.weightKg)
        _state.value = current.copy(date = date, existing = existing, showError = false)
    }

    fun dismiss() {
        _state.value = null
    }

    fun save() {
        val current = _state.value ?: return
        if (current.isSaving) return
        val kg = typedKg()
        if (kg == null || kg <= 0.0) {
            _state.value = current.copy(showError = true)
            return
        }
        _state.value = current.copy(isSaving = true, showError = false)
        scope.launch {
            try {
                repository.log(current.date, kg)
                _state.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it?.copy(isSaving = false) }
                _events.send(BodyWeightLogEvent.SaveFailed)
            }
        }
    }

    /** Deletes the weigh-in the sheet is on, and offers it back. */
    fun delete() {
        val entry = _state.value?.existing ?: return
        _state.value = null
        scope.launch {
            try {
                repository.delete(entry.id)
                _events.send(BodyWeightLogEvent.Deleted(entry))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(BodyWeightLogEvent.SaveFailed)
            }
        }
    }

    /** Undo for [delete]: logs the weigh-in again, on its day. */
    fun restore(entry: BodyWeightEntry) {
        scope.launch {
            try {
                repository.log(entry.date, entry.weightKg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(BodyWeightLogEvent.SaveFailed)
            }
        }
    }

    private fun openFor(date: LocalDate, editing: Boolean) {
        val existing = entries.firstOrNull { it.date == date }
        fill(existing?.weightKg ?: entries.maxByOrNull { it.date }?.weightKg)
        _state.value = BodyWeightLogState(date = date, existing = existing, isEditing = editing && existing != null)
    }

    /** The typed weight in kilograms: the filled value while untouched, else the text read in the user's unit. */
    private fun typedKg(): Double? {
        val text = weight.text.toString()
        if (text == filledText) return filledKg
        val typed = parseDecimal(text) ?: return null
        return if (unit == WeightUnit.KG) typed else lbToKg(typed)
    }

    /** Puts [kg] in the field in the user's unit, all selected, so typing replaces it. */
    private fun fill(kg: Double?) {
        val text = kg?.let { formatDecimalForInput(if (unit == WeightUnit.KG) it else kgToLb(it)) }.orEmpty()
        filledKg = kg
        filledText = text
        weight.edit {
            replace(0, length, text)
            selection = TextRange(0, text.length)
        }
    }
}
