package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.ExerciseWorkout
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressPoint
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.SetMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ExerciseProgressTest {

    private val today = LocalDate.of(2026, 9, 26)
    private val zone: ZoneId = ZoneOffset.UTC

    private fun lift(kg: Double, reps: Int) = SetMetrics.Strength(kg, reps)

    private fun workout(id: String, startedAt: String, vararg sets: SetMetrics) =
        ExerciseWorkout(workoutId = id, workoutName = "Push", startedAt = Instant.parse(startedAt), sets = sets.toList())

    @Test
    fun `strength charts the best estimate, the heaviest set and the volume`() {
        val sets = listOf(lift(100.0, 5), lift(90.0, 10), lift(80.0, 8))

        // 90 × 10 → 120 kg estimated beats 100 × 5 → 116.7 kg.
        assertEquals(oneRepMax(90.0, 10), ProgressMetric.ESTIMATED_ONE_REP_MAX.valueFor(sets)!!, 1e-9)
        assertEquals(100.0, ProgressMetric.MAX_WEIGHT.valueFor(sets)!!, 0.0)
        assertEquals(100.0 * 5 + 90.0 * 10 + 80.0 * 8, ProgressMetric.VOLUME.valueFor(sets)!!, 1e-9)
    }

    @Test
    fun `bodyweight charts reps, and cardio time and distance`() {
        val pullUps = listOf(SetMetrics.Bodyweight(12, null), SetMetrics.Bodyweight(8, 10.0))
        val runs = listOf(SetMetrics.Cardio(1_500, 5_000.0), SetMetrics.Cardio(600, null))

        assertEquals(12.0, ProgressMetric.MOST_REPS.valueFor(pullUps)!!, 0.0)
        assertEquals(20.0, ProgressMetric.TOTAL_REPS.valueFor(pullUps)!!, 0.0)
        assertEquals(2_100.0, ProgressMetric.TOTAL_DURATION.valueFor(runs)!!, 0.0)
        assertEquals(5_000.0, ProgressMetric.TOTAL_DISTANCE.valueFor(runs)!!, 0.0)
    }

    @Test
    fun `a metric the sets can't answer has no value, rather than a zero`() {
        val runs = listOf(SetMetrics.Cardio(1_500, null))

        assertNull(ProgressMetric.ESTIMATED_ONE_REP_MAX.valueFor(runs))
        assertNull(ProgressMetric.VOLUME.valueFor(runs))
        assertNull(ProgressMetric.TOTAL_DISTANCE.valueFor(runs))
        assertNull(ProgressMetric.TOTAL_DURATION.valueFor(listOf(lift(100.0, 5))))
    }

    @Test
    fun `each type offers what its sets record, the default first`() {
        assertEquals(
            listOf(ProgressMetric.ESTIMATED_ONE_REP_MAX, ProgressMetric.MAX_WEIGHT, ProgressMetric.VOLUME),
            ProgressMetric.forType(ExerciseType.STRENGTH),
        )
        assertEquals(listOf(ProgressMetric.MOST_REPS, ProgressMetric.TOTAL_REPS), ProgressMetric.forType(ExerciseType.BODYWEIGHT))
        assertEquals(listOf(ProgressMetric.TOTAL_DURATION, ProgressMetric.TOTAL_DISTANCE), ProgressMetric.forType(ExerciseType.CARDIO))
    }

    @Test
    fun `a series is one point per workout in range, oldest first`() {
        val workouts = listOf(
            workout("recent", "2026-09-20T18:00:00Z", lift(100.0, 5)),
            workout("old", "2026-05-01T18:00:00Z", lift(90.0, 5)),
            workout("edge", "2026-06-26T07:00:00Z", lift(95.0, 5)),
        )

        val series = progressSeries(workouts, ProgressMetric.MAX_WEIGHT, ProgressRange.THREE_MONTHS, today, zone)

        // Three months back from 26 September is 26 June, which is in.
        assertEquals(listOf("edge", "recent"), series.map { it.workoutId })
        assertEquals(listOf(LocalDate.of(2026, 6, 26), LocalDate.of(2026, 9, 20)), series.map { it.date })
        assertEquals(listOf("old", "edge", "recent"), progressSeries(workouts, ProgressMetric.MAX_WEIGHT, ProgressRange.ALL, today, zone).map { it.workoutId })
    }

    @Test
    fun `a workout's day is the one it started on where the user is`() {
        val lateEvening = workout("w", "2026-09-20T23:30:00Z", lift(100.0, 5))

        val series = progressSeries(listOf(lateEvening), ProgressMetric.MAX_WEIGHT, ProgressRange.ALL, today, ZoneId.of("Asia/Kathmandu"))

        assertEquals(LocalDate.of(2026, 9, 21), series.single().date)
    }

    @Test
    fun `workouts with nothing for the metric are left out`() {
        val workouts = listOf(
            workout("with", "2026-09-10T18:00:00Z", SetMetrics.Cardio(1_500, 5_000.0)),
            workout("without", "2026-09-12T18:00:00Z", SetMetrics.Cardio(1_500, null)),
        )

        assertEquals(listOf("with"), progressSeries(workouts, ProgressMetric.TOTAL_DISTANCE, ProgressRange.ALL, today, zone).map { it.workoutId })
    }

    @Test
    fun `a summary gives the first, the latest, the best and the change between`() {
        val series = progressSeries(
            listOf(
                workout("a", "2026-09-01T18:00:00Z", lift(100.0, 5)),
                workout("b", "2026-09-08T18:00:00Z", lift(110.0, 5)),
                workout("c", "2026-09-15T18:00:00Z", lift(105.0, 5)),
            ),
            ProgressMetric.MAX_WEIGHT,
            ProgressRange.ALL,
            today,
            zone,
        )

        val summary = series.summary()!!

        assertEquals("a", summary.first.workoutId)
        assertEquals("c", summary.latest.workoutId)
        assertEquals("b", summary.best.workoutId)
        assertEquals(5.0, summary.change, 1e-9)
        assertNull(emptyList<ProgressPoint>().summary())
    }

    @Test
    fun `ranges count back whole months`() {
        assertEquals(LocalDate.of(2026, 8, 26), ProgressRange.ONE_MONTH.startOn(today))
        assertEquals(LocalDate.of(2026, 3, 26), ProgressRange.SIX_MONTHS.startOn(today))
        assertEquals(LocalDate.of(2025, 9, 26), ProgressRange.ONE_YEAR.startOn(today))
        assertNull(ProgressRange.ALL.startOn(today))
    }
}
