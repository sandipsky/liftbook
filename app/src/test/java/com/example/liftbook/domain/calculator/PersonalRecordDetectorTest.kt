package com.example.liftbook.domain.calculator

import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordDetectorTest {

    private fun lift(weightKg: Double, reps: Int, type: SetType = SetType.NORMAL) = LoggedSet(type, SetMetrics.Strength(weightKg, reps))
    private fun bodyweight(reps: Int, addedKg: Double? = null) = LoggedSet(SetType.NORMAL, SetMetrics.Bodyweight(reps, addedKg))

    @Test
    fun `the first time an exercise is done, nothing is a record`() {
        assertTrue(detectPersonalRecords(previous = emptyList(), current = listOf(lift(100.0, 5))).isEmpty())
    }

    @Test
    fun `more weight than ever is a heaviest-weight record`() {
        val records = detectPersonalRecords(previous = listOf(lift(95.0, 5)), current = listOf(lift(100.0, 3)))

        assertTrue(PersonalRecord.HeaviestWeight(100.0, 3) in records)
    }

    @Test
    fun `matching the best isn't a record`() {
        val records = detectPersonalRecords(previous = listOf(lift(100.0, 5)), current = listOf(lift(100.0, 5)))

        assertTrue(records.isEmpty())
    }

    @Test
    fun `a higher estimated 1RM is a record, even at a lighter weight`() {
        // 90 × 10 → 120 kg estimated; 100 × 3 → 110 kg.
        val records = detectPersonalRecords(previous = listOf(lift(100.0, 3)), current = listOf(lift(90.0, 10)))

        assertEquals(listOf(PersonalRecord.BestEstimatedOneRepMax(oneRepMax(90.0, 10), 90.0, 10)), records)
    }

    @Test
    fun `more reps at a weight done before is a record`() {
        val records = detectPersonalRecords(previous = listOf(lift(80.0, 8), lift(100.0, 5)), current = listOf(lift(80.0, 10)))

        assertEquals(listOf(PersonalRecord.MostReps(10, 80.0)), records)
    }

    @Test
    fun `reps at a weight never done before aren't a most-reps record`() {
        val records = detectPersonalRecords(previous = listOf(lift(80.0, 8), lift(100.0, 5)), current = listOf(lift(85.0, 3)))

        assertTrue(records.none { it is PersonalRecord.MostReps })
    }

    @Test
    fun `warm-ups count on neither side`() {
        // A heavy warm-up earlier doesn't block today's record, and a heavy warm-up today doesn't make one.
        val previous = listOf(lift(120.0, 1, SetType.WARMUP), lift(100.0, 5))
        assertTrue(PersonalRecord.HeaviestWeight(105.0, 3) in detectPersonalRecords(previous, listOf(lift(105.0, 3))))
        assertTrue(detectPersonalRecords(previous, listOf(lift(130.0, 1, SetType.WARMUP), lift(90.0, 5))).isEmpty())
    }

    @Test
    fun `the same weight typed in pounds groups with itself`() {
        // 225 lb stored twice may differ in the last bits; it's still the same weight.
        val weight = lbToKg(225.0)
        val records = detectPersonalRecords(previous = listOf(lift(weight, 5)), current = listOf(lift(weight + 1e-9, 6)))

        assertTrue(records.any { it is PersonalRecord.MostReps && it.reps == 6 })
        assertTrue(records.none { it is PersonalRecord.HeaviestWeight })
    }

    @Test
    fun `bodyweight sets set most-reps records at their added weight, and nothing else`() {
        val records = detectPersonalRecords(
            previous = listOf(bodyweight(10), bodyweight(6, addedKg = 10.0)),
            current = listOf(bodyweight(12), bodyweight(8, addedKg = 20.0)),
        )

        assertEquals(listOf(PersonalRecord.MostReps(12, 0.0)), records)
    }

    @Test
    fun `cardio has no records`() {
        val run = { seconds: Int -> LoggedSet(SetType.NORMAL, SetMetrics.Cardio(seconds, 5_000.0)) }

        assertTrue(detectPersonalRecords(previous = listOf(run(1_800)), current = listOf(run(1_500))).isEmpty())
    }
}
