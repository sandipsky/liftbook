package com.example.liftbook.data.repository

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.data.local.seed.ExerciseSeedCallback
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.RoutineDraft
import com.example.liftbook.domain.model.RoutineExerciseDraft
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutEdits
import com.example.liftbook.testing.MutableClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** Runs the repository against a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryImplTest {

    private val clock = MutableClock(Instant.parse("2026-09-25T18:30:00Z"))
    private lateinit var database: LiftBookDatabase
    private lateinit var routines: RoutineRepositoryImpl
    private lateinit var repository: WorkoutRepositoryImpl
    private val sql: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    private fun seeded(name: String) = ExerciseSeed.exercises.first { it.name == name }.id
    private val bench = seeded("Bench Press (Barbell)")
    private val pullUp = seeded("Pull-Up")
    private val plank = seeded("Plank")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .addCallback(ExerciseSeedCallback(clock))
            .allowMainThreadQueries()
            .build()
        routines = RoutineRepositoryImpl(database, database.routineDao(), clock)
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

    /** A routine whose targets carry every value, to check that only the applicable ones are copied. */
    private suspend fun pushRoutine(): String = routines.createRoutine(
        RoutineDraft(
            "Push",
            listOf(
                RoutineExerciseDraft(bench, SetTarget(sets = 3, reps = 5, weightKg = 82.5, durationSeconds = 30)),
                RoutineExerciseDraft(pullUp, SetTarget(sets = 2, reps = 8, weightKg = 10.0)),
                RoutineExerciseDraft(plank, SetTarget(sets = 1, reps = 3, durationSeconds = 60)),
            ),
        ),
    )

    private suspend fun active(): Workout = repository.observeActiveWorkout().first()!!

    /** Logs a finished workout of [exerciseId] with these completed sets, an hour before [clock]. */
    private suspend fun doneBefore(exerciseId: String, vararg sets: Pair<SetType, SetValues>) {
        val now = clock.instant
        clock.instant = now.minusSeconds(3_600)
        val workoutId = (repository.startEmpty("Earlier") as StartWorkoutResult.Started).workoutId
        val exerciseRowId = repository.addExercises(workoutId, listOf(exerciseId)).single()
        val setIds = active().exercises.single().sets.map { it.id }
        setIds.drop(sets.size).forEach { repository.removeSet(it) }
        repeat(sets.size - setIds.size) { repository.addSet(exerciseRowId) }
        active().exercises.single().sets.zip(sets).forEach { (set, planned) ->
            repository.setSetType(set.id, planned.first)
            repository.completeSet(set.id, planned.second, clock.instant(), rest = null)
        }
        repository.finishWorkout(workoutId, clock.instant().plusSeconds(1_800))
        clock.instant = now
    }

    @Test
    fun `starting a routine creates the active workout, pre-filled with its targets`() = runTest {
        val routineId = pushRoutine()

        val result = repository.startFromRoutine(routineId) as StartWorkoutResult.Started

        val workout = active()
        assertEquals(result.workoutId, workout.id)
        assertEquals("Push", workout.name)
        assertEquals(routineId, workout.routineId)
        assertEquals(clock.instant(), workout.startedAt)
        assertTrue(workout.isActive)
        assertNull(workout.rest)
        assertEquals(listOf(bench, pullUp, plank), workout.exercises.map { it.exercise.id })
        assertEquals(listOf(3, 2, 1), workout.exercises.map { it.sets.size })
        val sets = workout.exercises.flatMap { it.sets }
        assertTrue(sets.none { it.isCompleted })
        assertTrue(sets.all { it.setType == SetType.NORMAL })

        // Only what each type records: weight × reps, reps, time.
        assertEquals(SetValues(weightKg = 82.5, reps = 5), workout.exercises[0].sets.first().values)
        assertEquals(SetValues(reps = 8), workout.exercises[1].sets.first().values)
        assertEquals(SetValues(durationSeconds = 60), workout.exercises[2].sets.first().values)
    }

    @Test
    fun `last time fills what a routine leaves blank`() = runTest {
        doneBefore(bench, SetType.WARMUP to SetValues(40.0, 10), SetType.NORMAL to SetValues(80.0, 8), SetType.NORMAL to SetValues(85.0, 6))
        val routineId = routines.createRoutine(RoutineDraft("Push", listOf(RoutineExerciseDraft(bench, SetTarget(sets = 3, reps = 5)))))

        repository.startFromRoutine(routineId)

        // The routine's reps win; the weights come from last time's working sets.
        assertEquals(
            listOf(SetValues(80.0, 5), SetValues(85.0, 5), SetValues(85.0, 5)),
            active().exercises.single().sets.map { it.values },
        )
    }

    @Test
    fun `the started sets carry their workout and exercise, for the history queries`() = runTest {
        val workoutId = (repository.startFromRoutine(pushRoutine()) as StartWorkoutResult.Started).workoutId

        sql.query(
            "SELECT COUNT(*) FROM workout_sets AS s JOIN workout_exercises AS we ON we.id = s.workoutExerciseId " +
                "WHERE s.workoutId = we.workoutId AND s.exerciseId = we.exerciseId AND s.workoutId = ?",
            arrayOf<Any?>(workoutId),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(6, cursor.getInt(0))
        }
    }

    @Test
    fun `only one workout can be in progress`() = runTest {
        val push = pushRoutine()
        val pull = routines.createRoutine(RoutineDraft("Pull", listOf(RoutineExerciseDraft(pullUp, SetTarget(sets = 3)))))
        val first = (repository.startFromRoutine(push) as StartWorkoutResult.Started).workoutId

        assertEquals(StartWorkoutResult.OtherWorkoutActive(first, "Push"), repository.startFromRoutine(pull))
        assertEquals(StartWorkoutResult.OtherWorkoutActive(first, "Push"), repository.startEmpty("Evening workout"))
        assertEquals(1, rowCount("workouts"))
    }

    @Test
    fun `an empty workout starts with nothing in it`() = runTest {
        val result = repository.startEmpty("Evening workout") as StartWorkoutResult.Started

        val workout = active()
        assertEquals(result.workoutId, workout.id)
        assertEquals("Evening workout", workout.name)
        assertNull(workout.routineId)
        assertTrue(workout.exercises.isEmpty())
    }

    @Test
    fun `starting the routine already in progress resumes it`() = runTest {
        val push = pushRoutine()
        val first = (repository.startFromRoutine(push) as StartWorkoutResult.Started).workoutId

        assertEquals(StartWorkoutResult.Resumed(first), repository.startFromRoutine(push))
        assertEquals(1, rowCount("workouts"))
    }

    @Test
    fun `a missing routine starts nothing`() = runTest {
        assertEquals(StartWorkoutResult.RoutineNotFound, repository.startFromRoutine("gone"))
        assertNull(repository.observeActiveWorkout().first())
    }

    @Test
    fun `added exercises go at the end, with last time's sets or empty ones`() = runTest {
        doneBefore(pullUp, SetType.NORMAL to SetValues(reps = 12), SetType.FAILURE to SetValues(reps = 9))
        val workoutId = (repository.startFromRoutine(pushRoutine()) as StartWorkoutResult.Started).workoutId

        val added = repository.addExercises(workoutId, listOf(pullUp, seeded("Squat (Barbell)")))

        val workout = active()
        assertEquals(added, workout.exercises.takeLast(2).map { it.id })
        val pullUps = workout.exercises[3].sets
        assertEquals(listOf(SetType.NORMAL, SetType.FAILURE), pullUps.map { it.setType })
        assertEquals(listOf(12, 9), pullUps.map { it.reps })
        assertTrue(pullUps.none { it.isCompleted })
        // Never done: three empty sets.
        assertEquals(List(3) { SetValues() }, workout.exercises[4].sets.map { it.values })
    }

    @Test
    fun `exercises can be reordered and removed`() = runTest {
        repository.startFromRoutine(pushRoutine())
        val ids = active().exercises.map { it.id }

        repository.reorderExercises(active().id, listOf(ids[2], ids[0]))
        assertEquals(listOf(ids[2], ids[0], ids[1]), active().exercises.map { it.id })

        repository.removeExercise(ids[0])
        assertEquals(listOf(plank, pullUp), active().exercises.map { it.exercise.id })
        assertEquals(3, rowCount("workout_sets"))
    }

    @Test
    fun `an added set starts from the last working set`() = runTest {
        repository.startFromRoutine(pushRoutine())
        val benchRow = active().exercises.first()
        repository.setSetType(benchRow.sets.last().id, SetType.WARMUP)

        val setId = repository.addSet(benchRow.id)

        val sets = active().exercises.first().sets
        assertEquals(setId, sets.last().id)
        assertEquals(SetValues(weightKg = 82.5, reps = 5), sets.last().values)
        assertEquals(SetType.NORMAL, sets.last().setType)

        repository.removeSet(setId)
        assertEquals(3, active().exercises.first().sets.size)
    }

    @Test
    fun `typed values and notes are saved together`() = runTest {
        repository.startFromRoutine(pushRoutine())
        val workout = active()
        val set = workout.exercises.first().sets.first()

        repository.saveEdits(
            workout.id,
            WorkoutEdits(
                setValues = mapOf(set.id to SetValues(weightKg = 90.0, reps = 3)),
                exerciseNotes = mapOf(workout.exercises.first().id to "Pause at the chest"),
                workoutNoteChanged = true,
                workoutNote = "Short on time",
            ),
        )

        val saved = active()
        assertEquals(SetValues(90.0, 3), saved.exercises.first().sets.first().values)
        assertEquals("Pause at the chest", saved.exercises.first().note)
        assertEquals("Short on time", saved.note)
    }

    @Test
    fun `completing a set stores it, carries values forward and starts the rest`() = runTest {
        repository.startFromRoutine(pushRoutine())
        val sets = active().exercises.first().sets
        val rest = RestTimer(clock.instant(), clock.instant().plusSeconds(90))

        repository.completeSet(sets[0].id, SetValues(85.0, 5), clock.instant(), rest, carryForward = mapOf(sets[1].id to SetValues(85.0, 5)))

        val saved = active()
        val stored = saved.exercises.first().sets
        assertTrue(stored[0].isCompleted)
        assertEquals(clock.instant(), stored[0].completedAt)
        assertEquals(SetValues(85.0, 5), stored[0].values)
        assertEquals(SetValues(85.0, 5), stored[1].values)
        assertFalse(stored[1].isCompleted)
        assertEquals(rest, saved.rest)
    }

    @Test
    fun `undoing a set stops the rest only if that set started it`() = runTest {
        repository.startFromRoutine(pushRoutine())
        val sets = active().exercises.first().sets
        val first = clock.instant()
        repository.completeSet(sets[0].id, SetValues(82.5, 5), first, RestTimer(first, first.plusSeconds(90)))
        val second = first.plusSeconds(120)
        repository.completeSet(sets[1].id, SetValues(82.5, 5), second, RestTimer(second, second.plusSeconds(90)))

        assertFalse(repository.uncompleteSet(sets[0].id))
        assertEquals(second, active().rest?.startedAt)

        assertTrue(repository.uncompleteSet(sets[1].id))
        assertNull(active().rest)
        assertTrue(active().exercises.first().sets.none { it.isCompleted })
    }

    @Test
    fun `an exercise's rest time is kept for next time, and applies now`() = runTest {
        val routineId = routines.createRoutine(RoutineDraft("Push", listOf(RoutineExerciseDraft(bench, SetTarget(sets = 1)))))
        repository.startFromRoutine(routineId)
        val row = active().exercises.single()
        sql.execSQL("UPDATE workout_exercises SET restSecondsOverride = 60 WHERE id = ?", arrayOf<Any?>(row.id))

        repository.setExerciseRest(row.id, 150)

        val updated = active().exercises.single()
        assertEquals(150, updated.exercise.defaultRestSeconds)
        assertNull(updated.restSecondsOverride)
        assertEquals(150, database.exerciseDao().getById(bench)?.defaultRestSeconds)
    }

    @Test
    fun `finishing keeps what was done and drops the rest`() = runTest {
        val workoutId = (repository.startFromRoutine(pushRoutine()) as StartWorkoutResult.Started).workoutId
        val workout = active()
        val benchSet = workout.exercises[0].sets.first()
        repository.completeSet(benchSet.id, SetValues(82.5, 5), clock.instant(), RestTimer(clock.instant(), clock.instant().plusSeconds(90)))
        // The plank has a note, so it stays even with nothing done; the pull-ups go.
        repository.saveEdits(workoutId, WorkoutEdits(exerciseNotes = mapOf(workout.exercises[2].id to "Skipped: shoulder")))
        val finishedAt = clock.instant().plusSeconds(3_600)

        assertTrue(repository.finishWorkout(workoutId, finishedAt))

        assertNull(repository.observeActiveWorkout().first())
        val finished = repository.observeWorkout(workoutId).first()!!
        assertEquals(finishedAt, finished.finishedAt)
        assertNull(finished.rest)
        assertEquals(listOf(bench, plank), finished.exercises.map { it.exercise.id })
        assertEquals(listOf(benchSet.id), finished.exercises[0].sets.map { it.id })
        assertTrue(finished.exercises[1].sets.isEmpty())
        assertFalse(repository.finishWorkout(workoutId, finishedAt))
    }

    @Test
    fun `personal records are measured against earlier finished working sets`() = runTest {
        doneBefore(bench, SetType.WARMUP to SetValues(120.0, 1), SetType.NORMAL to SetValues(100.0, 5))
        val workoutId = (repository.startEmpty("Now") as StartWorkoutResult.Started).workoutId
        repository.addExercises(workoutId, listOf(bench))

        val previous = repository.previousSets(setOf(bench, pullUp), before = clock.instant())

        // The warm-up and the workout in progress are left out; pull-ups were never done.
        assertEquals(mapOf(bench to listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(100.0, 5)))), previous)
        assertTrue(repository.previousSets(setOf(bench), before = clock.instant().minusSeconds(7_200)).isEmpty())
    }

    @Test
    fun `discarding removes the workout and everything in it`() = runTest {
        val push = pushRoutine()
        val workoutId = (repository.startFromRoutine(push) as StartWorkoutResult.Started).workoutId

        repository.discardActiveWorkout(workoutId)

        assertNull(repository.observeActiveWorkout().first())
        assertEquals(0, rowCount("workouts"))
        assertEquals(0, rowCount("workout_exercises"))
        assertEquals(0, rowCount("workout_sets"))
        // And the routine can be started again.
        assertTrue(repository.startFromRoutine(push) is StartWorkoutResult.Started)
    }

    @Test
    fun `discarding never touches a finished workout`() = runTest {
        sql.execSQL(
            "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note) VALUES ('done', 'Push', NULL, 0, 3600000, NULL)",
        )

        repository.discardActiveWorkout("done")

        assertEquals(1, rowCount("workouts"))
        assertNull(repository.observeActiveWorkout().first())
    }

    private fun rowCount(table: String): Int = sql.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
}
