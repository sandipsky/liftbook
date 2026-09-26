package com.example.liftbook.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.data.local.seed.ExerciseSeedCallback
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseDraft
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
class ExerciseRepositoryImplTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: LiftBookDatabase
    private lateinit var repository: ExerciseRepositoryImpl
    private val sql: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    private val benchId = "c468cc5d-6e4d-3f7f-8dbf-0936a13591d5"
    private val squatId = ExerciseSeed.exercises.first { it.name == "Squat (Barbell)" }.id

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .addCallback(ExerciseSeedCallback(clock))
            .allowMainThreadQueries()
            .build()
        repository = ExerciseRepositoryImpl(database, database.exerciseDao(), database.setDao(), clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a new database opens with the built-in library`() = runTest {
        val library = repository.observeLibrary().first()

        assertEquals(ExerciseSeed.exercises.size, library.size)
        val bench = library.first { it.id == benchId }
        assertEquals("Bench Press (Barbell)", bench.name)
        assertEquals(MuscleGroup.CHEST, bench.primaryMuscle)
        assertEquals(Equipment.BARBELL, bench.equipment)
        assertEquals(ExerciseType.STRENGTH, bench.type)
        assertFalse(bench.isCustom)
        assertFalse(bench.isArchived)
        assertEquals(clock.instant(), bench.createdAt)
    }

    @Test
    fun `the library is sorted by name, ignoring case`() = runTest {
        val names = repository.observeLibrary().first().map { it.name }
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun `archiving moves an exercise out of the library and keeps its history`() = runTest {
        logFinishedWorkout(benchId)

        repository.archive(benchId)

        assertFalse(repository.observeLibrary().first().any { it.id == benchId })
        assertEquals(listOf(benchId), repository.observeArchived().first().map { it.id })
        assertTrue(repository.getExercise(benchId)!!.isArchived)
        assertNotNull(repository.observeLastSession(benchId).first())

        repository.restore(benchId)
        assertTrue(repository.observeLibrary().first().any { it.id == benchId })
    }

    @Test
    fun `history lists finished sessions newest first, with completed sets only`() = runTest {
        // Older workout: two completed sets and one left unfinished.
        insertWorkout("w1", "Push", day = 10, finished = true)
        insertWorkoutExercise("we1", "w1", benchId, position = 0)
        insertSet("s1", "we1", "w1", benchId, position = 0, weightKg = 80.0, reps = 5)
        insertSet("s2", "we1", "w1", benchId, position = 1, weightKg = 80.0, reps = 5)
        insertSet("s3", "we1", "w1", benchId, position = 2, weightKg = 85.0, reps = 3, completed = false)
        // Newer workout with bench twice, the second entry a drop set.
        insertWorkout("w2", "Upper", day = 15, finished = true)
        insertWorkoutExercise("we2", "w2", benchId, position = 1)
        insertSet("s4", "we2", "w2", benchId, position = 0, weightKg = 82.5, reps = 5)
        insertWorkoutExercise("we3", "w2", benchId, position = 3)
        insertSet("s5", "we3", "w2", benchId, position = 0, weightKg = 60.0, reps = 12, setType = "DROP")
        // Excluded: a finished workout where no bench set was completed…
        insertWorkout("w3", "Legs", day = 18, finished = true)
        insertWorkoutExercise("we4", "w3", benchId, position = 0)
        insertSet("s6", "we4", "w3", benchId, position = 0, weightKg = 80.0, reps = 5, completed = false)
        // …the active workout…
        insertWorkout("w4", "Now", day = 20, finished = false)
        insertWorkoutExercise("we5", "w4", benchId, position = 0)
        insertSet("s7", "we5", "w4", benchId, position = 0, weightKg = 85.0, reps = 5)
        // …and other exercises.
        insertWorkoutExercise("we6", "w3", squatId, position = 1)
        insertSet("s8", "we6", "w3", squatId, position = 0, weightKg = 120.0, reps = 5)

        val page = database.setDao().exerciseSessions(benchId).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val sessions = page.data.map { it.toDomain() }

        assertEquals(listOf("we2", "we3", "we1"), sessions.map { it.workoutExerciseId })
        assertEquals(listOf("Upper", "Upper", "Push"), sessions.map { it.workoutName })
        assertEquals(listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(82.5, 5))), sessions[0].sets)
        assertEquals(listOf(LoggedSet(SetType.DROP, SetMetrics.Strength(60.0, 12))), sessions[1].sets)
        assertEquals(
            List(2) { LoggedSet(SetType.NORMAL, SetMetrics.Strength(80.0, 5)) },
            sessions[2].sets,
        )
        assertEquals("we2", repository.observeLastSession(benchId).first()?.workoutExerciseId)
    }

    @Test
    fun `an exercise that was never performed has no last session`() = runTest {
        assertNull(repository.observeLastSession(benchId).first())
        assertFalse(repository.hasLoggedSets(benchId))
    }

    @Test
    fun `custom exercises are created with a tidied name`() = runTest {
        val id = repository.createCustomExercise(
            ExerciseDraft("  Cable   Y-Raise ", MuscleGroup.SHOULDERS, Equipment.CABLE, ExerciseType.STRENGTH),
        )

        val created = repository.getExercise(id)!!
        assertEquals("Cable Y-Raise", created.name)
        assertTrue(created.isCustom)
        assertEquals(clock.instant(), created.createdAt)
        assertTrue(repository.observeLibrary().first().any { it.id == id })
    }

    @Test
    fun `custom exercises can be edited`() = runTest {
        val id = repository.createCustomExercise(
            ExerciseDraft("Cable Y-Raise", MuscleGroup.SHOULDERS, Equipment.CABLE, ExerciseType.STRENGTH),
        )

        repository.updateCustomExercise(
            id,
            ExerciseDraft("Band Y-Raise", MuscleGroup.SHOULDERS, Equipment.BAND, ExerciseType.BODYWEIGHT),
        )

        val updated = repository.getExercise(id)!!
        assertEquals("Band Y-Raise", updated.name)
        assertEquals(Equipment.BAND, updated.equipment)
        assertEquals(ExerciseType.BODYWEIGHT, updated.type)
    }

    @Test
    fun `built-in exercises can't be edited`() = runTest {
        val draft = ExerciseDraft("Renamed", MuscleGroup.CHEST, Equipment.BARBELL, ExerciseType.STRENGTH)
        assertFailsWith<IllegalArgumentException> { repository.updateCustomExercise(benchId, draft) }
        assertEquals("Bench Press (Barbell)", repository.getExercise(benchId)!!.name)
    }

    @Test
    fun `the type can't change once sets are logged, but everything else can`() = runTest {
        val id = repository.createCustomExercise(
            ExerciseDraft("Cable Y-Raise", MuscleGroup.SHOULDERS, Equipment.CABLE, ExerciseType.STRENGTH),
        )
        logFinishedWorkout(id)
        assertTrue(repository.hasLoggedSets(id))

        assertFailsWith<IllegalArgumentException> {
            repository.updateCustomExercise(
                id,
                ExerciseDraft("Cable Y-Raise", MuscleGroup.SHOULDERS, Equipment.CABLE, ExerciseType.CARDIO),
            )
        }
        repository.updateCustomExercise(
            id,
            ExerciseDraft("Cable Y Raise", MuscleGroup.BACK, Equipment.CABLE, ExerciseType.STRENGTH),
        )
        assertEquals("Cable Y Raise", repository.getExercise(id)!!.name)
    }

    @Test
    fun `an exercise with logged sets can't be deleted out from under its history`() = runTest {
        logFinishedWorkout(benchId)

        assertThrows(SQLiteConstraintException::class.java) {
            sql.execSQL("DELETE FROM exercises WHERE id = ?", arrayOf<Any?>(benchId))
        }
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

    private fun logFinishedWorkout(exerciseId: String) {
        insertWorkout("w-$exerciseId", "Push", day = 20, finished = true)
        insertWorkoutExercise("we-$exerciseId", "w-$exerciseId", exerciseId, position = 0)
        insertSet("s-$exerciseId", "we-$exerciseId", "w-$exerciseId", exerciseId, position = 0, weightKg = 20.0, reps = 10)
    }

    private fun insertWorkout(id: String, name: String, day: Int, finished: Boolean) {
        val startedAt = Instant.parse("2026-09-${day.toString().padStart(2, '0')}T18:00:00Z")
        sql.execSQL(
            "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note) VALUES (?, ?, NULL, ?, ?, NULL)",
            arrayOf<Any?>(id, name, startedAt.toEpochMilli(), if (finished) startedAt.plusSeconds(3_600).toEpochMilli() else null),
        )
    }

    private fun insertWorkoutExercise(id: String, workoutId: String, exerciseId: String, position: Int) {
        sql.execSQL(
            "INSERT INTO workout_exercises (id, workoutId, exerciseId, position, note, restSecondsOverride) " +
                "VALUES (?, ?, ?, ?, NULL, NULL)",
            arrayOf<Any?>(id, workoutId, exerciseId, position),
        )
    }

    private fun insertSet(
        id: String,
        workoutExerciseId: String,
        workoutId: String,
        exerciseId: String,
        position: Int,
        weightKg: Double,
        reps: Int,
        completed: Boolean = true,
        setType: String = "NORMAL",
    ) {
        sql.execSQL(
            "INSERT INTO workout_sets (id, workoutExerciseId, workoutId, exerciseId, position, setType, isCompleted, " +
                "weightKg, reps, durationSeconds, distanceMeters, completedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL)",
            arrayOf<Any?>(id, workoutExerciseId, workoutId, exerciseId, position, setType, if (completed) 1 else 0, weightKg, reps),
        )
    }
}
