package com.example.liftbook.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.local.entity.WorkoutScheduleEntity
import com.example.liftbook.domain.model.WorkoutMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Migrations are tested against the committed schema JSON (architecture §2.12). Room's
 * MigrationTestHelper reads schemas from instrumentation assets, which Robolectric unit tests
 * don't have, so this builds the old database straight from app/schemas and then lets Room open
 * it — running the migration and checking the result against the current schema, which throws
 * if they differ.
 */
@RunWith(RobolectricTestRunner::class)
class LiftBookDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun `version 1 to 2 keeps every workout and adds an empty rest timer`() = runTest {
        createFromSchema(version = 1) { db ->
            db.execSQL("INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note) VALUES ('done', 'Push', NULL, 0, 3600000, 'Good')")
            db.execSQL("INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note) VALUES ('live', 'Pull', NULL, 7200000, NULL, NULL)")
        }

        val database = Room.databaseBuilder(context, LiftBookDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            // Opening runs the migration and validates the migrated schema.
            val active = database.workoutDao().observeActive().first()!!
            assertEquals("live", active.workout.id)
            assertNull(active.workout.restStartedAt)
            assertNull(active.workout.restEndsAt)
            database.openHelper.readableDatabase.query("SELECT COUNT(*), note FROM workouts WHERE id = 'done'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals("Good", cursor.getString(1))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 2 to 3 keeps every workout and adds an empty body-weight log`() = runTest {
        createFromSchema(version = 2) { db ->
            db.execSQL(
                "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note, restStartedAt, restEndsAt) " +
                    "VALUES ('done', 'Push', NULL, 0, 3600000, 'Good', NULL, NULL)",
            )
        }

        val database = Room.databaseBuilder(context, LiftBookDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            // Opening runs the migration and validates the migrated schema.
            assertEquals(emptyList<Any>(), database.bodyWeightDao().observeAll().first())
            assertEquals("Push", database.workoutDao().getById("done")?.name)
            // The new table works, one weigh-in a day.
            database.bodyWeightDao().insert(
                BodyWeightEntryEntity("e", 82.4, LocalDate.of(2026, 9, 26), null),
            )
            assertEquals(82.4, database.bodyWeightDao().getOn(LocalDate.of(2026, 9, 26))?.weightKg)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 3 to 4 keeps every routine and workout and adds an empty schedule`() = runTest {
        createFromSchema(version = 3) { db ->
            db.execSQL("INSERT INTO routines (id, name, notes, createdAt, updatedAt) VALUES ('push', 'Push', NULL, 0, 0)")
            db.execSQL(
                "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note, restStartedAt, restEndsAt) " +
                    "VALUES ('done', 'Push', 'push', 0, 3600000, NULL, NULL, NULL)",
            )
        }

        val database = Room.databaseBuilder(context, LiftBookDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            // Opening runs the migration and validates the migrated schema.
            assertEquals(emptyList<Any>(), database.scheduleDao().getAll())
            assertEquals("push", database.workoutDao().getById("done")?.routineId)
            // The new table works, and links to a routine.
            database.scheduleDao().insert(
                WorkoutScheduleEntity(
                    id = "s",
                    daysOfWeek = 1,
                    startTimeMinutes = 18 * 60,
                    routineId = "push",
                    leadTimeMinutes = null,
                    isEnabled = true,
                    snoozedUntil = null,
                    skippedOn = null,
                    createdAt = Instant.EPOCH,
                ),
            )
            assertEquals("Push", database.scheduleDao().getAll().single().routineName)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 4 to 5 keeps every workout, each logged live`() = runTest {
        createFromSchema(version = 4) { db ->
            db.execSQL(
                "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note, restStartedAt, restEndsAt) " +
                    "VALUES ('done', 'Push', NULL, 0, 3600000, 'Good', NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note, restStartedAt, restEndsAt) " +
                    "VALUES ('live', 'Pull', NULL, 7200000, NULL, NULL, 7300000, 7390000)",
            )
        }

        val database = Room.databaseBuilder(context, LiftBookDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            // Opening runs the migration and validates the migrated schema.
            val done = database.workoutDao().getById("done")!!
            assertEquals(WorkoutMode.LIVE, done.mode)
            assertEquals("Good", done.note)
            val active = database.workoutDao().observeActive().first()!!
            assertEquals("live", active.workout.id)
            assertEquals(WorkoutMode.LIVE, active.workout.mode)
            assertEquals(Instant.ofEpochMilli(7390000), active.workout.restEndsAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 5 to 6 deletes retired built-ins that were never used and archives the ones that were`() = runTest {
        val landmine = "fd65a9ed-a57f-3358-ba00-6be74ab76e75"
        val rowing = "4a300d9d-e11e-31e5-9350-a00cbba6b629"
        val swimming = "7ec91ba5-f11b-36a0-97e8-c7a8aa079306"
        createFromSchema(version = 5) { db ->
            val exercise = "INSERT INTO exercises (id, name, primaryMuscle, equipment, type, isCustom, isArchived, defaultRestSeconds, notes, createdAt) VALUES "
            db.execSQL("$exercise ('$landmine', 'Landmine Press', 'SHOULDERS', 'BARBELL', 'STRENGTH', 0, 0, NULL, NULL, 0)")
            db.execSQL("$exercise ('$rowing', 'Rowing (Machine)', 'CARDIO', 'CARDIO_MACHINE', 'CARDIO', 0, 0, NULL, NULL, 0)")
            db.execSQL("$exercise ('$swimming', 'Swimming', 'CARDIO', 'NONE', 'CARDIO', 0, 0, NULL, NULL, 0)")
            db.execSQL("$exercise ('bench', 'Bench Press (Barbell)', 'CHEST', 'BARBELL', 'STRENGTH', 0, 0, NULL, NULL, 0)")
            // Rowing was logged in a workout and Swimming sits in a routine; Landmine Press was never used.
            db.execSQL(
                "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note, restStartedAt, restEndsAt, mode) " +
                    "VALUES ('w', 'Cardio', NULL, 0, 3600000, NULL, NULL, NULL, 'LIVE')",
            )
            db.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position, note, restSecondsOverride) VALUES ('we', 'w', '$rowing', 0, NULL, NULL)")
            db.execSQL(
                "INSERT INTO workout_sets (id, workoutExerciseId, workoutId, exerciseId, position, setType, isCompleted, weightKg, reps, durationSeconds, distanceMeters, completedAt) " +
                    "VALUES ('s', 'we', 'w', '$rowing', 0, 'NORMAL', 1, NULL, NULL, 600, 2000.0, 600000)",
            )
            db.execSQL("INSERT INTO routines (id, name, notes, createdAt, updatedAt) VALUES ('r', 'Swim day', NULL, 0, 0)")
            db.execSQL(
                "INSERT INTO routine_exercises (id, routineId, exerciseId, position, targetSets, targetReps, targetWeightKg, targetDurationSeconds, targetDistanceMeters, restSecondsOverride, notes) " +
                    "VALUES ('re', 'r', '$swimming', 0, 1, NULL, NULL, 1200, NULL, NULL, NULL)",
            )
        }

        val database = Room.databaseBuilder(context, LiftBookDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            // Opening runs the migration and validates the migrated schema.
            val archivedById = HashMap<String, Int>()
            database.openHelper.readableDatabase.query("SELECT id, isArchived FROM exercises").use { cursor ->
                while (cursor.moveToNext()) archivedById[cursor.getString(0)] = cursor.getInt(1)
            }
            assertEquals(mapOf(rowing to 1, swimming to 1, "bench" to 0), archivedById)
            // The history and routine that referred to them are untouched.
            assertEquals("Cardio", database.workoutDao().getById("w")?.name)
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM routine_exercises WHERE exerciseId = '$swimming'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    /** Creates the database exactly as schema [version] describes it, then runs [populate] on it. */
    private fun createFromSchema(version: Int, populate: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMA_DIR, "$version.json").readText()).getJSONObject("database")
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: continue
                            for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper -> populate(helper.writableDatabase) }
    }

    private companion object {
        /** Unit tests run in the module directory. */
        val SCHEMA_DIR = File("schemas/com.example.liftbook.data.local.LiftBookDatabase")
    }
}
