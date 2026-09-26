package com.example.liftbook.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.data.local.seed.ExerciseSeedCallback
import com.example.liftbook.domain.calculator.summaryWeeks
import com.example.liftbook.domain.calculator.weeklySummary
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.testing.MutableClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Progress reads (FR-5.1, FR-5.3) against a real, in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {

    private val clock = MutableClock(Instant.parse("2026-09-26T09:00:00Z"))
    private lateinit var database: LiftBookDatabase
    private lateinit var workouts: WorkoutRepositoryImpl
    private lateinit var progress: ProgressRepositoryImpl

    private fun seeded(name: String) = ExerciseSeed.exercises.first { it.name == name }.id
    private val bench = seeded("Bench Press (Barbell)")
    private val pullUp = seeded("Pull-Up")
    private val squat = seeded("Squat (Barbell)")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .addCallback(ExerciseSeedCallback(clock))
            .allowMainThreadQueries()
            .build()
        workouts = WorkoutRepositoryImpl(database, database.workoutDao(), database.setDao(), database.routineDao(), database.exerciseDao(), clock)
        progress = ProgressRepositoryImpl(database.progressDao())
    }

    @After
    fun tearDown() = database.close()

    /** Logs a workout at [startedAt] with exactly these completed sets; finishes it unless [finish] is off. */
    private suspend fun log(
        startedAt: String,
        vararg exercises: Pair<String, List<Pair<SetType, SetValues>>>,
        finish: Boolean = true,
    ): String {
        val start = Instant.parse(startedAt)
        clock.instant = start
        val id = (workouts.startEmpty("Workout") as StartWorkoutResult.Started).workoutId
        exercises.forEach { (exerciseId, sets) ->
            val rowId = workouts.addExercises(id, listOf(exerciseId)).single()
            workouts.observeWorkout(id).first()!!.exercises.first { it.id == rowId }.sets.forEach { workouts.removeSet(it.id) }
            sets.forEach { (type, values) ->
                val setId = workouts.addSet(rowId)
                workouts.setSetType(setId, type)
                workouts.completeSet(setId, values, start.plusSeconds(60), rest = null)
            }
        }
        if (finish) assertTrue(workouts.finishWorkout(id, start.plusSeconds(3_600)))
        return id
    }

    private fun working(kg: Double, reps: Int) = SetType.NORMAL to SetValues(kg, reps)

    @Test
    fun `an exercise's workouts come oldest first, with only their working sets`() = runTest {
        val newer = log("2026-09-20T18:00:00Z", bench to listOf(SetType.WARMUP to SetValues(40.0, 10), working(100.0, 5)))
        val older = log("2026-09-10T18:00:00Z", bench to listOf(working(95.0, 5), working(90.0, 8)), squat to listOf(working(140.0, 5)))
        // The workout in progress isn't progress yet.
        log("2026-09-26T08:00:00Z", bench to listOf(working(120.0, 5)), finish = false)

        val benchWorkouts = progress.observeExerciseWorkouts(bench).first()

        assertEquals(listOf(older, newer), benchWorkouts.map { it.workoutId })
        assertEquals(listOf(SetMetrics.Strength(95.0, 5), SetMetrics.Strength(90.0, 8)), benchWorkouts[0].sets)
        // The warm-up is left out at the source (FR-3.10).
        assertEquals(listOf(SetMetrics.Strength(100.0, 5)), benchWorkouts[1].sets)
    }

    @Test
    fun `an exercise done twice in a workout is one entry with both its sets`() = runTest {
        clock.instant = Instant.parse("2026-09-20T18:00:00Z")
        val id = (workouts.startEmpty("Push") as StartWorkoutResult.Started).workoutId
        val rows = workouts.addExercises(id, listOf(bench, pullUp, bench))
        workouts.observeWorkout(id).first()!!.exercises.flatMap { it.sets }.forEach { workouts.removeSet(it.id) }
        listOf(rows[0] to SetValues(100.0, 5), rows[1] to SetValues(reps = 10), rows[2] to SetValues(80.0, 10)).forEach { (row, values) ->
            workouts.completeSet(workouts.addSet(row), values, clock.instant(), rest = null)
        }
        workouts.finishWorkout(id, clock.instant().plusSeconds(3_600))

        val benchWorkouts = progress.observeExerciseWorkouts(bench).first()

        assertEquals(1, benchWorkouts.size)
        assertEquals(setOf(SetMetrics.Strength(100.0, 5), SetMetrics.Strength(80.0, 10)), benchWorkouts.single().sets.toSet())
    }

    @Test
    fun `a week's sets are counted per muscle, per workout, without warm-ups`() = runTest {
        log(
            "2026-09-22T18:00:00Z",
            bench to listOf(SetType.WARMUP to SetValues(40.0, 10), working(100.0, 5), working(100.0, 5)),
            pullUp to listOf(SetType.NORMAL to SetValues(reps = 10)),
        )
        log("2026-09-24T18:00:00Z", bench to listOf(working(100.0, 5)))
        log("2026-09-01T18:00:00Z", squat to listOf(working(140.0, 5)))

        val span = summaryWeeks(LocalDate.of(2026, 9, 26), DayOfWeek.MONDAY, ZoneOffset.UTC)
        val muscleSets = progress.observeMuscleSets(span.from, span.until).first()
        val summary = weeklySummary(
            workouts.observeFinishedBetween(span.from, span.until).first(),
            muscleSets,
            LocalDate.of(2026, 9, 26),
            DayOfWeek.MONDAY,
            ZoneOffset.UTC,
        )

        // Squat's workout is weeks earlier, so it's in neither.
        assertEquals(mapOf(MuscleGroup.CHEST to 3, MuscleGroup.BACK to 1), summary.current.setsByMuscle)
        assertEquals(2, summary.current.workouts)
        assertEquals(100.0 * 5 * 3, summary.current.volumeKg, 1e-9)
        assertEquals(0, summary.previous.workouts)
    }

    @Test
    fun `trained exercises come most recently done first, with how often`() = runTest {
        log("2026-09-10T18:00:00Z", bench to listOf(working(95.0, 5)), squat to listOf(working(140.0, 5)))
        log("2026-09-20T18:00:00Z", bench to listOf(working(100.0, 5)))
        // Only warm-ups: nothing to chart, so not listed.
        log("2026-09-21T18:00:00Z", pullUp to listOf(SetType.WARMUP to SetValues(reps = 5)))

        val trained = progress.observeTrainedExercises().first()

        assertEquals(listOf(bench, squat), trained.map { it.exercise.id })
        assertEquals(Instant.parse("2026-09-20T18:00:00Z"), trained[0].lastPerformedAt)
        assertEquals(2, trained[0].workouts)
        assertEquals(1, trained[1].workouts)
    }

    @Test
    fun `editing a past workout changes its progress at once`() = runTest {
        val id = log("2026-09-20T18:00:00Z", bench to listOf(working(100.0, 5)))
        val workout = workouts.observeWorkout(id).first()!!
        val set = workout.exercises.single().sets.single()
        database.setDao().setValues(set.id, 110.0, 5, null, null)

        assertEquals(listOf(SetMetrics.Strength(110.0, 5)), progress.observeExerciseWorkouts(bench).first().single().sets)
    }
}
