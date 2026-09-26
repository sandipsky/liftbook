package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.core.unit.milesToMeters
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SetFieldsTest {

    private val us = Locale.US

    @Test
    fun `a set shows as text in the user's units`() {
        val fields = SetFields.from(SetValues(weightKg = 82.5, reps = 8, durationSeconds = 90, distanceMeters = 5_000.0), WeightUnit.KG, us)

        assertEquals(listOf("82.5", "8", "130", "5"), fields.texts())
    }

    @Test
    fun `untouched values read back exactly`() {
        val values = SetValues(weightKg = 82.5, reps = 8, durationSeconds = 90, distanceMeters = 5_000.0)

        assertEquals(values, SetFields.from(values, WeightUnit.KG, us).values())
    }

    @Test
    fun `an untouched weight in pounds doesn't drift through its rounded text`() {
        // 80 kg is 176.3698… lb, shown as "176.37"; re-parsing that would store 79.99999 kg.
        val fields = SetFields.from(SetValues(weightKg = 80.0), WeightUnit.LB, us)
        assertEquals("176.37", fields.weight.text)

        assertEquals(80.0, fields.values().weightKg!!, 0.0)
    }

    @Test
    fun `typed values are read in the user's units and stored metric`() {
        val fields = SetFields.from(SetValues(), WeightUnit.LB, us)
        fields.weight.state.setTextAndPlaceCursorAtEnd("225")
        fields.reps.state.setTextAndPlaceCursorAtEnd("5")
        fields.duration.state.setTextAndPlaceCursorAtEnd("2000")
        fields.distance.state.setTextAndPlaceCursorAtEnd("3,1")

        val values = fields.values()
        assertEquals(lbToKg(225.0), values.weightKg!!, 0.0)
        assertEquals(5, values.reps)
        assertEquals(20 * 60, values.durationSeconds)
        assertEquals(milesToMeters(3.1), values.distanceMeters!!, 1e-9)
    }

    @Test
    fun `a cleared field is no value`() {
        val fields = SetFields.from(SetValues(weightKg = 80.0, reps = 8), WeightUnit.KG, us)
        fields.reps.state.setTextAndPlaceCursorAtEnd("")

        assertNull(fields.values().reps)
    }

    @Test
    fun `once saved, the text stands for the value saved`() {
        val fields = SetFields.from(SetValues(), WeightUnit.LB, us)
        fields.weight.state.setTextAndPlaceCursorAtEnd("176.37")
        val saved = SetValues(weightKg = 80.0)

        fields.markSaved(saved, fields.texts())

        assertEquals(80.0, fields.values().weightKg!!, 0.0)
    }

    @Test
    fun `filling shows the values and reads them back exactly`() {
        val fields = SetFields.from(SetValues(), WeightUnit.LB, us)

        fields.fill(SetValues(weightKg = 80.0, reps = 8))

        assertEquals("176.37", fields.weight.text)
        assertEquals("8", fields.reps.text)
        assertEquals(SetValues(weightKg = 80.0, reps = 8), fields.values())
    }

    @Test
    fun `a note is trimmed, blank is no note, and it knows when it's changed`() {
        val note = NoteText("Felt good")
        assertFalse(note.hasChanged())

        note.state.setTextAndPlaceCursorAtEnd("   ")
        assertNull(note.current())
        assertTrue(note.hasChanged())

        note.state.setTextAndPlaceCursorAtEnd("  Felt good ")
        assertFalse(note.hasChanged())
    }
}
