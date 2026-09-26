package com.example.liftbook.data.repository

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.data.local.seed.ExerciseSeedCallback
import com.example.liftbook.domain.model.RoutineDraft
import com.example.liftbook.domain.model.RoutineExerciseDraft
import com.example.liftbook.domain.model.SetTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Runs the repository against a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
class RoutineRepositoryImplTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: LiftBookDatabase
    private lateinit var repository: RoutineRepositoryImpl
    private val sql: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    private fun seeded(name: String) = ExerciseSeed.exercises.first { it.name == name }.id
    private val bench = seeded("Bench Press (Barbell)")
    private val squat = seeded("Squat (Barbell)")
    private val pullUp = seeded("Pull-Up")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .addCallback(ExerciseSeedCallback(clock))
            .allowMainThreadQueries()
            .build()
        repository = RoutineRepositoryImpl(database, database.routineDao(), clock)
    }

    @After
    fun tearDown() = database.close()

    private fun draft(name: String, vararg exercises: RoutineExerciseDraft) = RoutineDraft(name, exercises.toList())

    private fun item(exerciseId: String, sets: Int = 3, reps: Int? = 10, weightKg: Double? = null, id: String? = null) =
        RoutineExerciseDraft(exerciseId, SetTarget(sets = sets, reps = reps, weightKg = weightKg), id)

    @Test
    fun `a routine is created with its exercises in order`() = runTest {
        val id = repository.createRoutine(
            draft("  Push   day ", item(bench, sets = 4, reps = 6, weightKg = 80.0), item(pullUp, reps = 8)),
        )

        val routine = repository.getRoutine(id)!!
        assertEquals("Push day", routine.name)
        assertEquals(listOf(bench, pullUp), routine.exercises.map { it.exercise.id })
        assertEquals(SetTarget(sets = 4, reps = 6, weightKg = 80.0), routine.exercises[0].target)
        assertEquals(clock.instant(), routine.createdAt)
        assertNull(routine.lastPerformedAt)
    }

    @Test
    fun `routines are listed by name, ignoring case`() = runTest {
        repository.createRoutine(draft("push", item(bench)))
        repository.createRoutine(draft("Legs", item(squat)))
        repository.createRoutine(draft("Pull", item(pullUp)))

        assertEquals(listOf("Legs", "Pull", "push"), repository.observeRoutines().first().map { it.name })
    }

    @Test
    fun `an update reorders, keeps, adds and removes exercises`() = runTest {
        val id = repository.createRoutine(draft("Full body", item(bench), item(squat), item(pullUp)))
        val (benchRow, squatRow, _) = repository.getRoutine(id)!!.exercises
        // Something the editor doesn't carry, to check it survives the edit.
        sql.execSQL("UPDATE routine_exercises SET restSecondsOverride = 180 WHERE id = ?", arrayOf<Any?>(squatRow.id))

        repository.updateRoutine(
            id,
            draft(
                "Full body A",
                item(squat, sets = 5, reps = 5, weightKg = 100.0, id = squatRow.id),
                item(bench, id = benchRow.id),
                item(pullUp),
            ),
        )

        val updated = repository.getRoutine(id)!!
        assertEquals("Full body A", updated.name)
        assertEquals(listOf(squat, bench, pullUp), updated.exercises.map { it.exercise.id })
        assertEquals(squatRow.id, updated.exercises[0].id)
        assertEquals(benchRow.id, updated.exercises[1].id)
        assertEquals(SetTarget(sets = 5, reps = 5, weightKg = 100.0), updated.exercises[0].target)
        assertEquals(180, updated.exercises[0].restSecondsOverride)
        assertEquals(3, rowCount("routine_exercises"))
    }

    @Test
    fun `an exercise removed in an edit loses its row`() = runTest {
        val id = repository.createRoutine(draft("Push", item(bench), item(pullUp)))
        val benchRow = repository.getRoutine(id)!!.exercises.first()

        repository.updateRoutine(id, draft("Push", item(bench, id = benchRow.id)))

        assertEquals(listOf(benchRow.id), repository.getRoutine(id)!!.exercises.map { it.id })
        assertEquals(1, rowCount("routine_exercises"))
    }

    @Test
    fun `a duplicate copies everything under the next free name`() = runTest {
        val id = repository.createRoutine(draft("Push", item(bench, sets = 4, weightKg = 80.0), item(pullUp)))
        repository.createRoutine(draft("Push 2", item(bench)))

        val copyId = repository.duplicateRoutine(id)

        val source = repository.getRoutine(id)!!
        val copy = repository.getRoutine(copyId)!!
        assertEquals("Push 3", copy.name)
        assertEquals(source.exercises.map { it.exercise.id to it.target }, copy.exercises.map { it.exercise.id to it.target })
        assertTrue(copy.exercises.map { it.id }.none { it in source.exercises.map { item -> item.id } })
        assertEquals(clock.instant(), copy.createdAt)
    }

    @Test
    fun `last performed is the latest finished workout from the routine`() = runTest {
        val push = repository.createRoutine(draft("Push", item(bench)))
        val legs = repository.createRoutine(draft("Legs", item(squat)))
        insertWorkout("w1", routineId = push, day = 10, finished = true)
        insertWorkout("w2", routineId = push, day = 18, finished = true)
        insertWorkout("w3", routineId = legs, day = 20, finished = true)
        // In progress, so not performed yet.
        insertWorkout("w4", routineId = push, day = 24, finished = false)

        val routines = repository.observeRoutines().first().associateBy { it.id }
        assertEquals(Instant.parse("2026-09-18T18:00:00Z"), routines.getValue(push).lastPerformedAt)
        assertEquals(Instant.parse("2026-09-20T18:00:00Z"), routines.getValue(legs).lastPerformedAt)
    }

    @Test
    fun `deleting a routine keeps the workouts done from it`() = runTest {
        val id = repository.createRoutine(draft("Push", item(bench), item(pullUp)))
        insertWorkout("w1", routineId = id, day = 10, finished = true)

        repository.deleteRoutine(id)

        assertNull(repository.getRoutine(id))
        assertEquals(0, rowCount("routine_exercises"))
        assertEquals(1, rowCount("workouts"))
        sql.query("SELECT routineId FROM workouts WHERE id = 'w1'").use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.isNull(0))
        }
    }

    @Test
    fun `an archived exercise stays in the routines that use it`() = runTest {
        val id = repository.createRoutine(draft("Push", item(bench)))

        database.exerciseDao().setArchived(bench, archived = true)

        val exercise = repository.getRoutine(id)!!.exercises.single().exercise
        assertEquals(bench, exercise.id)
        assertTrue(exercise.isArchived)
    }

    @Test
    fun `a routine needs a name and a valid set count`() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.createRoutine(draft("  ", item(bench))) }
        assertFailsWith<IllegalArgumentException> { repository.createRoutine(draft("Push", item(bench, sets = 0))) }
        assertFailsWith<IllegalArgumentException> {
            repository.createRoutine(draft("Push", item(bench, sets = SetTarget.MAX_SETS + 1)))
        }
        assertEquals(0, rowCount("routines"))
    }

    @Test
    fun `observing a routine follows its edits and deletion`() = runTest {
        val id = repository.createRoutine(draft("Push", item(bench)))
        assertEquals("Push", repository.observeRoutine(id).first()?.name)

        repository.updateRoutine(id, draft("Push A", item(bench)))
        assertNotEquals("Push", repository.observeRoutine(id).first()?.name)

        repository.deleteRoutine(id)
        assertNull(repository.observeRoutine(id).first())
    }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }

    private fun rowCount(table: String): Int = sql.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private fun insertWorkout(id: String, routineId: String, day: Int, finished: Boolean) {
        val startedAt = Instant.parse("2026-09-${day.toString().padStart(2, '0')}T18:00:00Z")
        sql.execSQL(
            "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note) VALUES (?, ?, ?, ?, ?, NULL)",
            arrayOf<Any?>(id, "Workout", routineId, startedAt.toEpochMilli(), if (finished) startedAt.plusSeconds(3_600).toEpochMilli() else null),
        )
    }
}
