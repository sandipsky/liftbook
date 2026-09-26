package com.example.liftbook.ui.components

import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SetFormattingTest {

    private val us = Locale.US

    @Test
    fun `weights display in the chosen unit`() {
        assertEquals("82.5", displayWeight(82.5, WeightUnit.KG, us))
        assertEquals("181.88", displayWeight(82.5, WeightUnit.LB, us))
    }

    @Test
    fun `a weight entered in pounds displays exactly as entered`() {
        // Stored in kilograms (FR-6.1), shown in pounds: no drift from the round trip.
        assertEquals("185", displayWeight(lbToKg(185.0), WeightUnit.LB, us))
        assertEquals("2.5", displayWeight(lbToKg(2.5), WeightUnit.LB, us))
    }

    @Test
    fun `distances follow the unit system`() {
        assertEquals("5.2", displayDistance(5_200.0, WeightUnit.KG, us))
        assertEquals("3.11", displayDistance(5_000.0, WeightUnit.LB, us))
    }

    @Test
    fun `warm-ups are not numbered, every other set is`() {
        val sets = listOf(SetType.WARMUP, SetType.WARMUP, SetType.NORMAL, SetType.NORMAL, SetType.DROP, SetType.FAILURE)
            .map { LoggedSet(it, SetMetrics.Strength(weightKg = 60.0, reps = 5)) }
        assertEquals(listOf(null, null, 1, 2, 3, 4), workingSetNumbers(sets))
    }

    @Test
    fun `no sets gives no numbers`() {
        assertEquals(emptyList<Int?>(), workingSetNumbers(emptyList()))
    }
}
