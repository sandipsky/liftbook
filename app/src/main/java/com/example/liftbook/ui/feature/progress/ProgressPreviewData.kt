package com.example.liftbook.ui.feature.progress

import com.example.liftbook.domain.calculator.bodyWeightTrend
import com.example.liftbook.domain.calculator.change
import com.example.liftbook.domain.calculator.progressSeries
import com.example.liftbook.domain.calculator.summary
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.ExerciseWorkout
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.TrainedExercise
import com.example.liftbook.domain.model.WeekSummary
import com.example.liftbook.domain.model.WeeklySummary
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Sample data for the progress screens' previews. */
internal object ProgressPreviewData {

    val zone: ZoneId = ZoneId.of("UTC")
    val today: LocalDate = LocalDate.of(2026, 9, 26)

    private fun exercise(id: String, name: String, muscle: MuscleGroup, equipment: Equipment, type: ExerciseType = ExerciseType.STRENGTH) =
        Exercise(id, name, muscle, equipment, type, isCustom = false, isArchived = false, createdAt = today.atStartOfDay(zone).toInstant())

    val bench = exercise("bench", "Bench Press (Barbell)", MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat = exercise("squat", "Squat (Barbell)", MuscleGroup.QUADS, Equipment.BARBELL)
    private val pullUp = exercise("pull-up", "Pull-Up", MuscleGroup.BACK, Equipment.NONE, ExerciseType.BODYWEIGHT)
    private val ohp = exercise("ohp", "Overhead Press (Barbell)", MuscleGroup.SHOULDERS, Equipment.BARBELL)

    /** Twice a week for three months, the top set creeping up. */
    private val benchWorkouts: List<ExerciseWorkout> = listOf(
        80.0 to 5, 80.0 to 6, 82.5 to 5, 82.5 to 5, 82.5 to 6, 85.0 to 4, 85.0 to 5, 85.0 to 5,
        87.5 to 4, 85.0 to 6, 87.5 to 5, 87.5 to 5, 90.0 to 3, 87.5 to 6, 90.0 to 4, 90.0 to 5,
    ).mapIndexed { index, (kg, reps) ->
        val daysAgo = (15 - index) * 5L + 1
        ExerciseWorkout(
            workoutId = "w$index",
            workoutName = if (index % 2 == 0) "Push" else "Upper",
            startedAt = today.minusDays(daysAgo).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant(),
            sets = listOf(SetMetrics.Strength(kg, reps), SetMetrics.Strength(kg - 5, reps + 2), SetMetrics.Strength(kg - 5, reps + 1)),
        )
    }

    fun benchProgress(): ExerciseProgressUiState {
        val metric = ProgressMetric.ESTIMATED_ONE_REP_MAX
        val points = progressSeries(benchWorkouts, metric, ProgressRange.THREE_MONTHS, today, zone)
        return ExerciseProgressUiState(
            isLoading = false,
            exercise = bench,
            metrics = ProgressMetric.forType(bench.type),
            metric = metric,
            range = ProgressRange.THREE_MONTHS,
            points = points,
            summary = points.summary(),
            hasHistory = true,
            metricHasValues = true,
            today = today,
            zone = zone,
        )
    }

    val week = WeeklySummary(
        current = WeekSummary(
            start = LocalDate.of(2026, 9, 21),
            workouts = 3,
            volumeKg = 12_340.0,
            setsByMuscle = mapOf(MuscleGroup.CHEST to 12, MuscleGroup.BACK to 9, MuscleGroup.QUADS to 8, MuscleGroup.SHOULDERS to 6, MuscleGroup.TRICEPS to 4),
        ),
        previous = WeekSummary(
            start = LocalDate.of(2026, 9, 14),
            workouts = 4,
            volumeKg = 15_200.0,
            setsByMuscle = mapOf(MuscleGroup.CHEST to 10, MuscleGroup.BACK to 12, MuscleGroup.QUADS to 8, MuscleGroup.HAMSTRINGS to 6, MuscleGroup.SHOULDERS to 3),
        ),
    )

    /** Most mornings for three months, drifting down through the day-to-day noise. */
    private val weighIns: List<BodyWeightEntry> = (0 until 45).map { index ->
        val noise = listOf(0.3, -0.2, 0.4, -0.4, 0.1, 0.5, -0.3, 0.0, 0.2)[index % 9]
        BodyWeightEntry(id = "bw$index", date = today.minusDays(88L - index * 2), weightKg = 84.0 - index * 0.04 + noise)
    }

    fun bodyWeight(): BodyWeightUiState {
        val trend = bodyWeightTrend(weighIns)
        return BodyWeightUiState(
            isLoading = false,
            range = ProgressRange.THREE_MONTHS,
            entries = weighIns,
            trend = trend,
            change = trend.change(),
            hasEntries = true,
            today = today,
        )
    }

    fun progress(): ProgressUiState {
        val trend = bodyWeightTrend(weighIns)
        val daysAgo = { days: Long -> today.minusDays(days).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant() }
        return ProgressUiState(
            isLoading = false,
            week = week,
            exercises = listOf(
                TrainedExercise(bench, daysAgo(1), 16),
                TrainedExercise(pullUp, daysAgo(1), 14),
                TrainedExercise(squat, daysAgo(3), 12),
                TrainedExercise(ohp, daysAgo(10), 6),
            ),
            bodyWeight = BodyWeightGlance(latest = weighIns.last(), trend = trend, change = trend.change()),
            today = today,
            zone = zone,
        )
    }
}
