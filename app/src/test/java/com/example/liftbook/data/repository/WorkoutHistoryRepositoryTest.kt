package com.example.liftbook.data.repository

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.data.local.seed.ExerciseSeedCallback
import com.example.liftbook.data.mapper.toListItem
import com.example.liftbook.domain.calculator.summarize
import com.example.liftbook.domain.model.ExerciseRevision
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetRevision
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.domain.model.WorkoutRevision
import com.example.liftbook.testing.MutableClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** History (FR-4.1, FR-4.2, FR-4.4) against a real, in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class WorkoutHistoryRepositoryTest {

    private val clock = MutableClock(Instant.parse("2026-09-25T18:30:00Z"))
    private lateinit var database: LiftBookDatabase
    private lateinit var repository: WorkoutRepositoryImpl

    private fun seeded(name: String) = ExerciseSeed.exercises.first { it.name == name }.id
    private val bench = seeded("Bench Press (Barbell)")
    private val pullUp = seeded("Pull-Up")
    private val plank = seeded("Plank")
    private val squat = seeded("Squat (Barbell)")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .addCallback(ExerciseSeedCallback(clock))
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepositoryImpl(
            database,
            database.workoutDao(),
            database.setDao(),
            database.routineDao(),
            database.exerciseDao(),
            clock,
        )
    }

    @After
    fun tearDown() = database.close()

    /** Logs and finishes a workout that ran [minutes] from [startedAt], with exactly these completed sets. */
    private suspend fun log(
        name: String,
        startedAt: String,
        vararg exercises: Pair<String, List<Pair<SetType, SetValues>>>,
        minutes: Long = 60,
    ): String {
        val start = Instant.parse(startedAt)
        clock.instant = start
        val id = (repository.startEmpty(name) as StartWorkoutResult.Started).workoutId
        exercises.forEach { (exerciseId, sets) ->
            val rowId = repository.addExercises(id, listOf(exerciseId)).single()
            workout(id).exercises.first { it.id == rowId }.sets.forEach { repository.removeSet(it.id) }
            sets.forEach { (type, values) ->
                val setId = repository.addSet(rowId)
                repository.setSetType(setId, type)
                repository.completeSet(setId, values, start.plusSeconds(60), rest = null)
            }
        }
        assertTrue(repository.finishWorkout(id, start.plusSeconds(minutes * 60)))
        return id
    }

    private suspend fun workout(id: String): Workout = repository.observeWorkout(id).first()!!

    private suspend fun historyPage(): List<WorkoutListItem> {
        val page = database.workoutDao().finishedWorkouts().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        return page.data.map { it.toListItem() }
    }

    @Test
    fun `history lists finished workouts newest first, with their totals`() = runTest {
        val older = log(
            "Push",
            "2026-09-20T18:00:00Z",
            bench to listOf(SetType.WARMUP to SetValues(40.0, 10), SetType.NORMAL to SetValues(80.0, 8), SetType.DROP to SetValues(60.0, 10)),
            pullUp to listOf(SetType.NORMAL to SetValues(reps = 10)),
            plank to listOf(SetType.NORMAL to SetValues(durationSeconds = 60)),
            minutes = 62,
        )
        val newer = log("Legs", "2026-09-23T07:00:00Z", squat to listOf(SetType.NORMAL to SetValues(100.0, 5)), minutes = 45)
        // The workout in progress isn't history.
        clock.instant = Instant.parse("2026-09-25T18:00:00Z")
        repository.startEmpty("Now")

        val history = historyPage()

        assertEquals(listOf(newer, older), history.map { it.id })
        val push = history.last()
        // Warm-ups, reps-only and timed sets add no volume (FR-3.10): 80 × 8 + 60 × 10.
        assertEquals(1_240.0, push.volumeKg, 0.0)
        assertEquals(5, push.completedSets)
        assertEquals(62 * 60L, push.durationSeconds)
        assertEquals(listOf("Bench Press (Barbell)", "Pull-Up", "Plank"), push.exerciseNames)
    }

    @Test
    fun `the list's volume is the summary's`() = runTest {
        val id = log(
            "Push",
            "2026-09-20T18:00:00Z",
            bench to listOf(SetType.WARMUP to SetValues(40.0, 10), SetType.FAILURE to SetValues(82.5, 6)),
            pullUp to listOf(SetType.NORMAL to SetValues(reps = 12)),
        )

        assertEquals(workout(id).summarize(emptyMap()).volumeKg, historyPage().single().volumeKg, 1e-9)
    }

    @Test
    fun `a month's workouts are those that started in it, oldest first`() = runTest {
        log("Before", "2026-08-31T23:59:00Z")
        val first = log("First", "2026-09-01T00:00:00Z")
        val last = log("Last", "2026-09-30T20:00:00Z")
        log("After", "2026-10-01T00:00:00Z")

        val september = repository.observeFinishedBetween(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")).first()

        assertEquals(listOf(first, last), september.map { it.id })
    }

    @Test
    fun `a revision edits the workout in place`() = runTest {
        val id = log(
            "Push",
            "2026-09-20T18:00:00Z",
            bench to listOf(SetType.NORMAL to SetValues(80.0, 8), SetType.NORMAL to SetValues(80.0, 7)),
            pullUp to listOf(SetType.NORMAL to SetValues(reps = 10)),
        )
        val before = workout(id)
        val (benchRow, pullUpRow) = before.exercises
        val keptSet = benchRow.sets.first()

        val saved = repository.saveRevision(
            id,
            WorkoutRevision(
                name = "  Push   day ",
                // A day earlier, and half an hour long.
                startedAt = Instant.parse("2026-09-19T18:00:00Z"),
                finishedAt = Instant.parse("2026-09-19T18:30:00Z"),
                note = "Short on time",
                exercises = listOf(
                    // Pull-ups first now, with a new exercise after the bench.
                    ExerciseRevision(pullUpRow.id, pullUp, note = null, sets = listOf(SetRevision(pullUpRow.sets.single().id, SetType.NORMAL, SetMetrics.Bodyweight(12, null)))),
                    ExerciseRevision(
                        benchRow.id,
                        bench,
                        note = "Paused reps",
                        sets = listOf(
                            SetRevision(keptSet.id, SetType.WARMUP, SetMetrics.Strength(60.0, 10)),
                            SetRevision("new-set", SetType.NORMAL, SetMetrics.Strength(85.0, 5)),
                        ),
                    ),
                    ExerciseRevision("new-exercise", squat, note = null, sets = listOf(SetRevision("new-squat", SetType.NORMAL, SetMetrics.Strength(100.0, 5)))),
                ),
            ),
        )

        assertTrue(saved)
        val after = workout(id)
        assertEquals("Push day", after.name)
        assertEquals(Instant.parse("2026-09-19T18:00:00Z"), after.startedAt)
        assertEquals(Instant.parse("2026-09-19T18:30:00Z"), after.finishedAt)
        assertEquals("Short on time", after.note)
        assertEquals(listOf(pullUpRow.id, benchRow.id, "new-exercise"), after.exercises.map { it.id })
        val benchAfter = after.exercises[1]
        assertEquals("Paused reps", benchAfter.note)
        // The kept set is changed in place; the second set is gone, and the new one added.
        assertEquals(listOf(keptSet.id, "new-set"), benchAfter.sets.map { it.id })
        assertEquals(SetType.WARMUP, benchAfter.sets[0].setType)
        assertEquals(SetValues(60.0, 10), benchAfter.sets[0].values)
        assertTrue(after.exercises.flatMap { it.sets }.all { it.isCompleted })
        // The kept set moved back a day with the workout; the new one is done at its end.
        assertEquals(keptSet.completedAt!!.minusSeconds(24 * 3_600), benchAfter.sets[0].completedAt)
        assertEquals(after.finishedAt, benchAfter.sets[1].completedAt)
        assertEquals(12, after.exercises[0].sets.single().reps)
        // Totals follow the sets (FR-4.2): the warm-up doesn't count.
        assertEquals(85.0 * 5 + 100.0 * 5, historyPage().single().volumeKg, 0.0)
    }

    @Test
    fun `a revision drops exercises left with nothing in them`() = runTest {
        val id = log("Push", "2026-09-20T18:00:00Z", bench to listOf(SetType.NORMAL to SetValues(80.0, 8)), pullUp to listOf(SetType.NORMAL to SetValues(reps = 10)))
        val (benchRow, pullUpRow) = workout(id).exercises
        val benchSet = benchRow.sets.single()

        repository.saveRevision(
            id,
            WorkoutRevision(
                name = "Push",
                startedAt = Instant.parse("2026-09-20T18:00:00Z"),
                finishedAt = Instant.parse("2026-09-20T19:00:00Z"),
                note = null,
                exercises = listOf(
                    ExerciseRevision(benchRow.id, bench, note = null, sets = listOf(SetRevision(benchSet.id, benchSet.setType, SetMetrics.Strength(80.0, 8)))),
                    ExerciseRevision(pullUpRow.id, pullUp, note = null, sets = emptyList()),
                ),
            ),
        )

        assertEquals(listOf(benchRow.id), workout(id).exercises.map { it.id })
    }

    @Test
    fun `only a finished workout takes a revision`() = runTest {
        val active = (repository.startEmpty("Now") as StartWorkoutResult.Started).workoutId
        val revision = WorkoutRevision("Now", clock.instant(), clock.instant().plusSeconds(60), note = null, exercises = emptyList())

        assertFalse(repository.saveRevision(active, revision))
        assertFalse(repository.saveRevision("missing", revision))
        assertEquals("Now", workout(active).name)
        assertNull(workout(active).finishedAt)
    }

    @Test
    fun `deleting a finished workout removes it and its sets, and nothing else`() = runTest {
        val id = log("Push", "2026-09-20T18:00:00Z", bench to listOf(SetType.NORMAL to SetValues(80.0, 8)))
        val kept = log("Legs", "2026-09-21T18:00:00Z", squat to listOf(SetType.NORMAL to SetValues(100.0, 5)))
        clock.instant = Instant.parse("2026-09-25T18:00:00Z")
        val active = (repository.startEmpty("Now") as StartWorkoutResult.Started).workoutId

        repository.deleteFinishedWorkout(id)
        repository.deleteFinishedWorkout(active)

        assertNull(repository.observeWorkout(id).first())
        assertEquals(listOf(kept), historyPage().map { it.id })
        assertTrue(database.setDao().getForWorkout(id).isEmpty())
        assertFalse(database.setDao().hasSetsForExercise(bench))
        // The workout in progress is never touched from here.
        assertNotNull(repository.observeWorkout(active).first())
    }
}
