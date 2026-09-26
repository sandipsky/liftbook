package com.example.liftbook.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.backup.BackupCodec
import com.example.liftbook.data.backup.BackupFile
import com.example.liftbook.data.backup.BackupRows
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.data.local.seed.ExerciseSeedCallback
import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.BackupProblem
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseDraft
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.RoutineDraft
import com.example.liftbook.domain.model.RoutineExerciseDraft
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.testing.FakeBackupDocuments
import com.example.liftbook.testing.InMemoryPreferencesDataStore
import com.example.liftbook.testing.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** Export, import and clearing (FR-6.3–6.5) against real, in-memory Room databases: one per phone. */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val clock = MutableClock(Instant.parse("2026-09-26T10:00:00Z"))
    private val documents = FakeBackupDocuments()
    private val file = DocumentUri("content://downloads/liftbook-backup.json")
    private val phones = mutableListOf<Phone>()

    private fun seeded(name: String) = ExerciseSeed.exercises.first { it.name == name }.id
    private val bench = seeded("Bench Press (Barbell)")
    private val squat = seeded("Squat (Barbell)")
    private val pullUp = seeded("Pull-Up")

    private inner class Phone {
        val database: LiftBookDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .addCallback(ExerciseSeedCallback(clock))
            .allowMainThreadQueries()
            .build()
        val dataStore = InMemoryPreferencesDataStore()
        val backup = BackupRepositoryImpl(database, database.backupDao(), dataStore, documents, clock, Dispatchers.Unconfined)
        val settings = SettingsRepositoryImpl(dataStore)
        val workouts = WorkoutRepositoryImpl(database, database.workoutDao(), database.setDao(), database.routineDao(), database.exerciseDao(), clock)
        val routines = RoutineRepositoryImpl(database, database.routineDao(), clock)
        val exercises = ExerciseRepositoryImpl(database, database.exerciseDao(), database.setDao(), clock)
        val bodyWeight = BodyWeightRepositoryImpl(database, database.bodyWeightDao())

        suspend fun rows(): BackupRows = database.withTransaction {
            val dao = database.backupDao()
            BackupRows(
                exercises = dao.getExercises(),
                routines = dao.getRoutines(),
                routineExercises = dao.getRoutineExercises(),
                workouts = dao.getFinishedWorkouts(),
                workoutExercises = dao.getFinishedWorkoutExercises(),
                sets = dao.getFinishedWorkoutSets(),
                bodyWeight = dao.getBodyWeight(),
            )
        }

        suspend fun counts(): DataCounts = backup.observeCounts().first()

        /** Logs and finishes a workout with exactly these completed sets. */
        suspend fun log(name: String, startedAt: String, vararg exercises: Pair<String, List<Pair<SetType, SetValues>>>): String {
            val start = Instant.parse(startedAt)
            clock.instant = start
            val id = (workouts.startEmpty(name) as StartWorkoutResult.Started).workoutId
            exercises.forEach { (exerciseId, sets) ->
                val rowId = workouts.addExercises(id, listOf(exerciseId)).single()
                workouts.observeWorkout(id).first()!!.exercises.first { it.id == rowId }.sets.forEach { workouts.removeSet(it.id) }
                sets.forEach { (type, values) ->
                    val setId = workouts.addSet(rowId)
                    workouts.setSetType(setId, type)
                    workouts.completeSet(setId, values, start.plusSeconds(60), rest = null)
                }
            }
            assertTrue(workouts.finishWorkout(id, start.plusSeconds(3_600)))
            return id
        }

        /** A bit of everything a backup carries, including a built-in's archiving and rest time. */
        suspend fun fill() {
            val zercher = exercises.createCustomExercise(ExerciseDraft("Zercher Squat", MuscleGroup.QUADS, Equipment.BARBELL, ExerciseType.STRENGTH))
            routines.createRoutine(
                RoutineDraft(
                    "Push",
                    listOf(
                        RoutineExerciseDraft(bench, SetTarget(sets = 3, reps = 8, weightKg = 80.0)),
                        RoutineExerciseDraft(pullUp, SetTarget(sets = 3, reps = 10)),
                    ),
                ),
            )
            log(
                "Push",
                "2026-09-20T18:00:00Z",
                bench to listOf(SetType.WARMUP to SetValues(40.0, 10), SetType.NORMAL to SetValues(82.5, 8)),
                pullUp to listOf(SetType.NORMAL to SetValues(reps = 10)),
            )
            log("Legs", "2026-09-22T07:00:00Z", zercher to listOf(SetType.NORMAL to SetValues(90.0, 5)), squat to listOf(SetType.FAILURE to SetValues(100.0, 6)))
            bodyWeight.log(LocalDate.of(2026, 9, 21), 82.4)
            bodyWeight.log(LocalDate.of(2026, 9, 24), 82.1)
            exercises.archive(seeded("Pec Deck"))
            database.exerciseDao().setDefaultRestSeconds(bench, 150)
            settings.setDefaultRestSeconds(120)
            settings.setFirstDayOfWeek(FirstDayOfWeek.SUNDAY)
            settings.setThemeMode(ThemeMode.DARK)
            clock.instant = Instant.parse("2026-09-26T10:00:00Z")
        }
    }

    private fun phone(): Phone = Phone().also { phones += it }

    private suspend fun problemImporting(phone: Phone, mode: ImportMode): BackupProblem? = try {
        phone.backup.import(file, mode)
        null
    } catch (e: BackupException) {
        e.problem
    }

    @After
    fun tearDown() = phones.forEach { it.database.close() }

    @Test
    fun `a backup restored onto a cleared phone brings back everything, settings included`() = runTest {
        val phone = phone()
        phone.fill()
        val before = phone.rows()
        val settings = phone.settings.userPreferences.first()

        val exported = phone.backup.export(file)
        phone.backup.clearAll()
        assertEquals(DataCounts(), phone.counts())
        val restored = phone.backup.import(file, ImportMode.REPLACE)

        assertEquals(DataCounts(workouts = 2, routines = 1, customExercises = 1, weighIns = 2), exported)
        assertEquals(exported, restored)
        assertEquals(before, phone.rows())
        assertEquals(settings, phone.settings.userPreferences.first())
    }

    @Test
    fun `merging into another phone adds the backup's history and keeps that phone's own`() = runTest {
        val old = phone()
        old.fill()
        old.backup.export(file)
        val new = phone()
        new.log("Pull", "2026-09-25T18:00:00Z", pullUp to listOf(SetType.NORMAL to SetValues(reps = 12)))
        new.settings.setThemeMode(ThemeMode.LIGHT)

        val added = new.backup.import(file, ImportMode.MERGE)

        assertEquals(DataCounts(workouts = 2, routines = 1, customExercises = 1, weighIns = 2), added)
        assertEquals(DataCounts(workouts = 3, routines = 1, customExercises = 1, weighIns = 2), new.counts())
        val oldRows = old.rows()
        val newRows = new.rows()
        assertTrue(newRows.sets.containsAll(oldRows.sets))
        assertTrue(newRows.workouts.containsAll(oldRows.workouts))
        // A merge leaves this phone's settings alone.
        assertEquals(ThemeMode.LIGHT, new.settings.userPreferences.first().themeMode)
        assertEquals(UserPreferences.DEFAULT_REST_SECONDS, new.settings.userPreferences.first().defaultRestSeconds)
    }

    @Test
    fun `merging a backup into the phone it came from adds nothing`() = runTest {
        val phone = phone()
        phone.fill()
        val before = phone.rows()
        phone.backup.export(file)

        assertEquals(DataCounts(), phone.backup.import(file, ImportMode.MERGE))
        assertEquals(before, phone.rows())
    }

    @Test
    fun `the workout in progress isn't backed up, and a replace ends it`() = runTest {
        val phone = phone()
        phone.fill()
        phone.workouts.startEmpty("Evening workout")

        val exported = phone.backup.export(file)
        phone.backup.import(file, ImportMode.REPLACE)

        assertEquals(2, exported.workouts)
        assertEquals(2, BackupCodec.decode(documents.files.getValue(file)).workouts.size)
        assertNull(phone.workouts.observeActiveWorkout().first())
    }

    @Test
    fun `clearing leaves LiftBook as it was installed`() = runTest {
        val phone = phone()
        phone.fill()
        phone.workouts.startEmpty("Evening workout")
        phone.backup.export(file)

        phone.backup.clearAll()

        assertEquals(DataCounts(), phone.counts())
        assertNull(phone.workouts.observeActiveWorkout().first())
        val library = phone.rows().exercises
        assertEquals(ExerciseSeed.exercises.map { it.id }.toSet(), library.map { it.id }.toSet())
        assertTrue(library.none { it.isArchived || it.defaultRestSeconds != null })
        // Settings are back to their defaults, and there's no backup of what's here now.
        assertEquals(ThemeMode.SYSTEM, phone.settings.userPreferences.first().themeMode)
        assertEquals(UserPreferences.DEFAULT_REST_SECONDS, phone.settings.userPreferences.first().defaultRestSeconds)
        assertNull(phone.backup.lastExportedAt.first())
    }

    @Test
    fun `a backup from before a built-in existed still leaves the whole library`() = runTest {
        val phone = phone()
        documents.files[file] = BackupCodec.encode(BackupFile(exportedAt = Instant.parse("2025-01-01T00:00:00Z")))

        phone.backup.import(file, ImportMode.REPLACE)

        assertEquals(ExerciseSeed.exercises.size, phone.rows().exercises.size)
    }

    @Test
    fun `a damaged backup changes nothing`() = runTest {
        val phone = phone()
        phone.fill()
        val before = phone.rows()
        documents.files[file] = """{"format": "liftbook-backup", "formatVersion": 1, "workouts": "lots"}"""

        assertEquals(BackupProblem.DAMAGED, problemImporting(phone, ImportMode.REPLACE))
        assertEquals(before, phone.rows())
    }

    @Test
    fun `a file that can't be read says so`() = runTest {
        val phone = phone()
        documents.failure = IOException("Provider gone")

        try {
            phone.backup.inspect(file)
            fail("Expected the read to fail")
        } catch (e: BackupException) {
            assertEquals(BackupProblem.UNREADABLE, e.problem)
        }
    }

    @Test
    fun `reading a backup says when it was made and what's in it, without importing`() = runTest {
        val old = phone()
        old.fill()
        old.backup.export(file)
        val new = phone()

        val summary = new.backup.inspect(file)

        assertEquals(clock.instant(), summary.exportedAt)
        assertEquals(DataCounts(workouts = 2, routines = 1, customExercises = 1, weighIns = 2), summary.counts)
        assertEquals(DataCounts(), new.counts())
    }

    @Test
    fun `an export records when it was made`() = runTest {
        val phone = phone()
        assertNull(phone.backup.lastExportedAt.first())

        phone.backup.export(file)

        assertEquals(clock.instant(), phone.backup.lastExportedAt.first())
        assertNotNull(documents.files[file])
    }
}
