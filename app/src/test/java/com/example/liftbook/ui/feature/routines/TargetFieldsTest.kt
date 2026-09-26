package com.example.liftbook.ui.feature.routines

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.core.unit.milesToMeters
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TargetFieldsTest {

    private val us = Locale.US

    @Test
    fun `a target shows as text in kilograms`() {
        val fields = TargetFields.from(SetTarget(sets = 3, reps = 8, weightKg = 82.5, durationSeconds = 90), WeightUnit.KG, us)

        assertEquals(TargetTexts(sets = "3", reps = "8", weight = "82.5", duration = "130", distance = ""), fields.texts())
    }

    @Test
    fun `an untouched target saves exactly as it was`() {
        val target = SetTarget(sets = 3, reps = 8, weightKg = 82.5, durationSeconds = 90, distanceMeters = 5_000.0)

        assertEquals(target, TargetFields.from(target, WeightUnit.KG, us).toTarget(WeightUnit.KG, us))
    }

    @Test
    fun `an untouched weight in pounds doesn't drift through its rounded text`() {
        // 80 kg is 176.3698… lb, shown as "176.37"; re-parsing that would store 79.99999 kg.
        val fields = TargetFields.from(SetTarget(sets = 3, weightKg = 80.0), WeightUnit.LB, us)
        assertEquals("176.37", fields.weight.text.toString())

        assertEquals(80.0, fields.toTarget(WeightUnit.LB, us)!!.weightKg!!, 0.0)
    }

    @Test
    fun `a weight typed in pounds is stored in kilograms`() {
        val fields = TargetFields.from(SetTarget(sets = 3), WeightUnit.LB, us)
        fields.weight.setTextAndPlaceCursorAtEnd("175")

        assertEquals(lbToKg(175.0), fields.toTarget(WeightUnit.LB, us)!!.weightKg!!, 0.0)
    }

    @Test
    fun `a distance typed in miles is stored in metres`() {
        val fields = TargetFields.from(SetTarget(sets = 1), WeightUnit.LB, us)
        fields.distance.setTextAndPlaceCursorAtEnd("3,1")

        assertEquals(milesToMeters(3.1), fields.toTarget(WeightUnit.LB, us)!!.distanceMeters!!, 1e-9)
    }

    @Test
    fun `blank or zero values mean no target`() {
        val fields = TargetFields.from(SetTarget(sets = 3, reps = 8, weightKg = 80.0, durationSeconds = 60), WeightUnit.KG, us)
        fields.reps.setTextAndPlaceCursorAtEnd("0")
        fields.weight.setTextAndPlaceCursorAtEnd("")
        fields.duration.setTextAndPlaceCursorAtEnd("0")

        assertEquals(SetTarget(sets = 3), fields.toTarget(WeightUnit.KG, us))
    }

    @Test
    fun `the set count must be between 1 and 20`() {
        val fields = TargetFields.from(SetTarget(sets = 3), WeightUnit.KG, us)
        listOf("", "0", "21").forEach { text ->
            fields.sets.setTextAndPlaceCursorAtEnd(text)
            assertFalse(fields.hasValidSets())
            assertNull(fields.toTarget(WeightUnit.KG, us))
        }
        fields.sets.setTextAndPlaceCursorAtEnd("20")
        assertEquals(20, fields.toTarget(WeightUnit.KG, us)!!.sets)
    }
}
